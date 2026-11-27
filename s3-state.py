#!/usr/bin/env python3
"""Save and restore S3 buckets for the two local emulator containers."""

import argparse
import json
from pathlib import Path
import shutil
import ssl
import subprocess
import tempfile
import time
from urllib.request import urlopen
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parent
CLI_IMAGE = 'public.ecr.aws/aws-cli/aws-cli:latest'
OBJECT_HEADERS = ('ContentType', 'ContentEncoding', 'ContentDisposition',
                  'ContentLanguage', 'CacheControl', 'Metadata')


class SnapshotVolume:
    def __init__(self, backend, container):
        self.name = f'localstack-aws-{backend}-s3-state'
        self.container = container

    def is_mounted(self):
        mounts = subprocess.check_output(
            ['docker', 'inspect', '--format', '{{json .Mounts}}', self.container], text=True,
        )
        return any(mount.get('Name') == self.name and mount['Destination'] == '/s3-state'
                   for mount in json.loads(mounts))

    def run(self, script, stdin=None, stdout=subprocess.PIPE):
        command = ['docker', 'exec', '--user', '0']
        if stdin is not None:
            command.append('-i')
        return subprocess.run(
            command + [self.container, '/bin/sh', '-ec', script],
            stdin=stdin, stdout=stdout, check=True,
        ).stdout

    def import_legacy(self, path):
        if path.is_file() and not self.run('if [ -e /s3-state/snapshot.zip ]; then printf exists; fi'):
            self.publish(path)

    def prepare_restore(self, path):
        if not self.run('if [ -f /s3-state/snapshot.zip ]; then printf exists; fi'):
            return False
        with path.open('wb') as output:
            self.run('touch /s3-state/restore-pending && cat /s3-state/snapshot.zip', stdout=output)
        return True

    def complete_restore(self):
        self.run('rm -f /s3-state/restore-pending')

    def require_complete_restore(self):
        if self.run('if [ -e /s3-state/restore-pending ]; then printf pending; fi'):
            raise RuntimeError(f'Retry S3 restore before replacing the snapshot in {self.name}')

    def publish(self, path):
        with path.open('rb') as source:
            self.run('''
                if [ -e /s3-state/restore-pending ]; then
                    echo 'S3 restore is pending' >&2
                    exit 1
                fi
                temporary=$(mktemp /s3-state/snapshot.XXXXXX)
                trap 'rm -f "$temporary"' EXIT
                cat > "$temporary"
                mv "$temporary" /s3-state/snapshot.zip
            ''', stdin=source)


class LocalS3:
    def __init__(self, backend, workdir):
        self.official = backend == 'aws-emu'
        self.endpoint = 'https://127.0.0.1:9297' if self.official else 'http://127.0.0.1:4566'
        self.container = 'aws-emulator' if self.official else 'localstack-aws'
        self.credentials = Path.home() / '.aws/emulator/9297'
        self.command = [
            'docker', 'run', '--rm', '--network', f'container:{self.container}',
            '-v', f'{workdir}:/work', '-e', 'AWS_DEFAULT_REGION=us-east-1',
            '-e', 'AWS_EC2_METADATA_DISABLED=true', '-e', 'AWS_PAGER=',
        ]
        if self.official:
            self.command += [
                '-v', f'{self.credentials}:/emulator:ro',
                '-e', 'AWS_CONFIG_FILE=/emulator/config',
                '-e', 'AWS_SHARED_CREDENTIALS_FILE=/emulator/emulator_credentials',
                '-e', 'AWS_CA_BUNDLE=/emulator/ca.pem',
            ]
        else:
            self.command += ['-e', 'AWS_ACCESS_KEY_ID=test', '-e', 'AWS_SECRET_ACCESS_KEY=test']
        self.command += [CLI_IMAGE, '--endpoint-url', self.endpoint, '--output', 'json', 's3api']

    def call(self, operation, *args, parameters=None):
        command = self.command + [operation, *args]
        if parameters is not None:
            command += ['--cli-input-json', json.dumps(parameters)]
        output = subprocess.check_output(command, text=True)
        return json.loads(output) if output.strip() else {}

    def wait_until_ready(self):
        route = '/__admin/health' if self.official else '/_localstack/health'
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            try:
                context = ssl.create_default_context(cafile=self.credentials / 'ca.pem') if self.official else None
                with urlopen(self.endpoint + route, context=context, timeout=2) as response:
                    response.read()
                return
            except OSError:
                time.sleep(1)
        raise RuntimeError(f'{self.container} did not become ready for S3 restore')


def save(s3, workdir, archive_path):
    manifest = []
    object_number = 0
    with ZipFile(archive_path, 'w') as archive:
        for bucket in s3.call('list-buckets').get('Buckets', []):
            name = bucket['Name']
            objects = []
            for obj in s3.call('list-objects-v2', f'--bucket={name}').get('Contents', []):
                key = obj['Key']
                member = f'objects/{object_number}'
                object_number += 1
                metadata = s3.call('get-object', f'--bucket={name}', f'--key={key}', '/work/object')
                archive.write(workdir / 'object', member)
                headers = {field: metadata[field] for field in OBJECT_HEADERS if field in metadata}
                objects.append({'key': key, 'member': member, 'headers': headers})
            manifest.append({'bucket': name, 'objects': objects})
        archive.writestr('manifest.json', json.dumps(manifest))
    return object_number, len(manifest)


def restore(s3, workdir, source):
    s3.wait_until_ready()
    existing = {bucket['Name'] for bucket in s3.call('list-buckets').get('Buckets', [])}
    count = 0
    with ZipFile(source) as archive:
        manifest = json.loads(archive.read('manifest.json'))
        for bucket in manifest:
            name = bucket['bucket']
            if name not in existing:
                s3.call('create-bucket', f'--bucket={name}')
            for obj in bucket['objects']:
                with (workdir / 'object').open('wb') as destination, archive.open(obj['member']) as payload:
                    while chunk := payload.read(1024 * 1024):
                        destination.write(chunk)
                s3.call('put-object', f'--bucket={name}', f'--key={obj["key"]}',
                        '--body=/work/object', parameters=obj['headers'])
                count += 1
    print(f'Restored {count} S3 objects in {len(manifest)} buckets.')


def save_legacy(snapshot, legacy):
    # An existing container needs one final backup before it can be recreated with the mount.
    legacy.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=legacy.parent) as directory:
        temporary = Path(directory) / 'snapshot.zip'
        shutil.copyfile(snapshot, temporary)
        temporary.replace(legacy)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('save', 'restore'))
    parser.add_argument('backend', choices=('aws-emu', 'localstack'))
    args = parser.parse_args()
    legacy = ROOT / '.s3-state' / args.backend / 'snapshot.zip'
    with tempfile.TemporaryDirectory() as directory:
        workdir = Path(directory).resolve()
        snapshot = workdir / 'snapshot.zip'
        s3 = LocalS3(args.backend, workdir)
        volume = SnapshotVolume(args.backend, s3.container)
        try:
            mounted = volume.is_mounted()
            if mounted:
                volume.import_legacy(legacy)
            if args.action == 'save':
                if mounted:
                    volume.require_complete_restore()
                objects, buckets = save(s3, workdir, snapshot)
                if mounted:
                    volume.publish(snapshot)
                    location = volume.name
                else:
                    save_legacy(snapshot, legacy)
                    location = f'{legacy} for migration on next start'
                print(f'Saved {objects} S3 objects in {buckets} buckets to {location}.')
            elif not mounted:
                raise RuntimeError('Restart the emulator with its start script to mount its S3 volume')
            elif volume.prepare_restore(snapshot):
                restore(s3, workdir, snapshot)
                volume.complete_restore()
        except (OSError, RuntimeError, subprocess.CalledProcessError, BadZipFile, ValueError) as error:
            parser.exit(1, f'S3 {args.action} failed: {error}\n')


if __name__ == '__main__':
    main()
