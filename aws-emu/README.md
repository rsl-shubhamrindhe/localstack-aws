# Official AWS Emulator on macOS

This guide builds and runs AWS Emulator in Docker, checks its HTTPS endpoint, and walks through creating an S3 bucket, uploading a file, and reading it back. The emulator runs on `https://127.0.0.1:9297`. You can complete the exercise without an AWS account or a LocalStack token. Spring Boot can stay stopped during the exercise. [AWS Emulator overview](https://docs.aws.amazon.com/emulator/latest/userguide/what-is-service.html)

## 1. Start Docker Desktop

Install Docker Desktop if needed, then open it and wait for the engine to start. Run these commands from the repository root.

```sh
docker info --format '{{.ServerVersion}}'
```

`docker info` queries the running Docker engine. `--format '{{.ServerVersion}}'` prints just its version, which confirms the terminal can reach it.

If Docker reports that it cannot connect to the daemon, check that Docker Desktop has finished starting. The AWS CLI will run in a container, so a separate installation on your Mac is optional.

## 2. Build the emulator image

```sh
docker build -t aws-emulator aws-emu
docker pull public.ecr.aws/aws-cli/aws-cli:latest
```

- `docker build -t aws-emulator aws-emu` builds from the `aws-emu/` directory. `-t aws-emulator` gives the image the name used in the startup command.
- `docker pull public.ecr.aws/aws-cli/aws-cli:latest` downloads the official AWS CLI container image from AWS’s public registry. `latest` is the image tag.

The [Dockerfile](Dockerfile) starts from Amazon Linux 2023 Minimal and installs the official AWS Emulator RPM from AWS. It chooses the `x86_64` RPM for Intel and the `aarch64` RPM for Apple silicon. The emulator listens on `0.0.0.0` inside the container so Docker can forward requests from your Mac. [AWS Docker guide](https://docs.aws.amazon.com/emulator/latest/userguide/getting-started-docker.html)

## 3. Start the emulator

From the repository root, run `./aws-emu/start.sh` after building the image in step 2. The script runs the following commands.

```sh
mkdir -p "$HOME/.aws/emulator"

docker run -d --rm --name aws-emulator \
  --memory=2g --memory-swap=2g \
  -p 127.0.0.1:9297:9297 \
  -e "HOME=$HOME" \
  -v "$HOME/.aws/emulator:$HOME/.aws/emulator" \
  aws-emulator --port 9297 --telemetry off
```

- `mkdir -p "$HOME/.aws/emulator"` creates the host directory for the emulator’s generated files. `-p` creates any missing parent directories and allows the directory to already exist. Quoting the path preserves any spaces.

| Command part | What it does |
| --- | --- |
| `docker run` | Creates and starts a container from an image. |
| `-d` | Runs the container in the background so your terminal remains available. |
| `--rm` | Automatically removes the container when it stops. |
| `--name aws-emulator` | Names the container so commands such as `docker logs aws-emulator` can refer to it. |
| `--memory=2g` | Limits the container to 2 GiB of RAM. |
| `--memory-swap=2g` | Sets the combined RAM and swap limit to 2 GiB. Because this equals the RAM limit, the container gets no additional swap. |
| `-p 127.0.0.1:9297:9297` | Maps port `9297` on your Mac to port `9297` in the container. The `127.0.0.1` binding makes it reachable only from your Mac. |
| `-e "HOME=$HOME"` | Sets `HOME` inside the container to the same path as your Mac’s home directory. |
| `-v "$HOME/.aws/emulator:$HOME/.aws/emulator"` | Mounts your Mac’s emulator directory at the same path inside the container, so both can access the generated certificate and credentials. |
| `aws-emulator` | Selects the image you built in step 2. Arguments after the image name go to the emulator process. |
| `--port 9297` | Tells the emulator to listen on port `9297` inside the container. |
| `--telemetry off` | Disables emulator telemetry for this run. |
| `\` | Continues the command on the next line. |

Docker publishes port `9297` only on your Mac’s loopback interface. The bind mount makes the generated TLS certificate, CLI configuration, and emulator credentials available under `~/.aws/emulator/9297/`. The emulator generates new credentials and a certificate at startup. AWS API requests are signed with the generated credentials. `--telemetry off` disables usage reporting for this run. [AWS SDK configuration](https://docs.aws.amazon.com/emulator/latest/userguide/point-sdk-to-emulator.html) · [AWS telemetry settings](https://docs.aws.amazon.com/emulator/latest/userguide/telemetry.html)

## 4. Verify the emulator is running

```sh
curl -fsS --cacert "$HOME/.aws/emulator/9297/ca.pem" \
  https://127.0.0.1:9297/__admin/health
```

`curl` sends an HTTPS GET to the health endpoint. `-f` makes HTTP errors fail the command, `-s` hides progress output, and `-S` still prints error messages. `--cacert` tells `curl` to verify TLS using the emulator’s CA certificate.

Expect `ok`. You can also open the [health endpoint](https://127.0.0.1:9297/__admin/health) in a browser. If your browser shows a certificate warning, the `curl` command above checks the endpoint using the emulator’s CA directly. The emulator exposes an API and has no web console for browsing buckets.

If the check fails, `docker ps --filter name=aws-emulator` lists running containers whose names match `aws-emulator`. While the container exists, `docker logs aws-emulator` prints its startup messages.

## 5. Point the AWS CLI at the emulator

Paste this function into your terminal. The CLI container shares the emulator’s network, so its `127.0.0.1` reaches the emulator. It mounts only the generated emulator files, read-only, and uses the local HTTPS endpoint.

```sh
aws_local() {
  docker run --rm -i --network container:aws-emulator \
    -v "$HOME/.aws/emulator/9297:/emulator:ro" \
    -e AWS_ENDPOINT_URL=https://127.0.0.1:9297 \
    -e AWS_CONFIG_FILE=/emulator/config \
    -e AWS_SHARED_CREDENTIALS_FILE=/emulator/emulator_credentials \
    -e AWS_CA_BUNDLE=/emulator/ca.pem \
    -e AWS_DEFAULT_REGION=us-east-1 \
    -e AWS_EC2_METADATA_DISABLED=true \
    -e AWS_PAGER= \
    public.ecr.aws/aws-cli/aws-cli:latest "$@"
}

aws_local sts get-caller-identity
```

- `aws_local() { ... }` defines a shell function. Its `"$@"` forwards the arguments you give it to the AWS CLI, preserving their boundaries. Paste the definition again in each new terminal session where you want to use it.
- `docker run --rm -i` starts a temporary CLI container and keeps standard input open so text can be piped to `aws s3 cp`. `--network container:aws-emulator` lets the CLI reach the emulator via the shared loopback interface.
- `-v "$HOME/.aws/emulator/9297:/emulator:ro"` exposes the files for this emulator instance at `/emulator` inside the CLI container. `:ro` makes the mount read-only.
- `AWS_ENDPOINT_URL` directs requests to the emulator. `AWS_CONFIG_FILE` and `AWS_SHARED_CREDENTIALS_FILE` select its generated configuration and signing credentials. `AWS_CA_BUNDLE` provides the CA for TLS verification.
- `AWS_DEFAULT_REGION` gives the CLI a default region. `AWS_EC2_METADATA_DISABLED=true` turns off EC2 instance metadata credential lookup. An empty `AWS_PAGER` prints output directly to the terminal.
- `aws_local sts get-caller-identity` asks the emulator’s Security Token Service (STS) which identity signed the request.

Expect account `000000000000` and ARN `arn:aws:iam::000000000000:user/emulator`. This confirms the CLI is talking to the local emulator. [AWS SDK configuration](https://docs.aws.amazon.com/emulator/latest/userguide/point-sdk-to-emulator.html)

## 6. Create a bucket and test an S3 object

An S3 **bucket** holds objects. The **key** identifies an object within its bucket. Here, the bucket is `aws-emulator-learning` and the key is `hello.txt`.

```sh
# Create the bucket on a fresh emulator run.
aws_local s3 mb s3://aws-emulator-learning

# Upload text from standard input as hello.txt.
printf 'Hello from AWS Emulator!\n' | \
  aws_local s3 cp - s3://aws-emulator-learning/hello.txt

# List the bucket and its objects.
aws_local s3 ls
aws_local s3 ls s3://aws-emulator-learning/

# Download the object to standard output.
aws_local s3 cp s3://aws-emulator-learning/hello.txt -
```

- `s3 mb` means “make bucket.” The `s3://` URI names the bucket.
- `printf` writes the message and a newline. The pipe (`|`) feeds those bytes into `s3 cp`. A `-` in the source position means read from standard input, and the destination URI gives the bucket and object key.
- `s3 ls` without a URI lists buckets. With the bucket URI, it lists objects in that bucket.
- `s3 cp` with `-` as the destination streams the object to standard output so you can read it in the terminal.

The object listing should include `hello.txt`, and the last command should print `Hello from AWS Emulator!`. If you are using the existing container where we ran this exercise, the bucket may already exist. Start with the list or download command to check it.

## 7. Stop and restart

From the repository root, run `./aws-emu/stop.sh` to stop the emulator and `./aws-emu/start.sh` to start it again.

The stop script saves a local S3 snapshot before Docker removes the container. The start script restores that snapshot after the emulator is ready. Running `docker stop aws-emulator` directly skips the backup. See [S3 persistence](../README.md#keep-s3-data-across-restarts) for what is saved and how to recover from a failed restore.

## Read an S3 object from Spring Boot

With the emulator running, select **Official AWS Emulator** in IntelliJ’s run configuration menu. The shared configuration runs `spring-boot:test-run` with the `local` profile. From the repository root, you can also select the official backend through the shared launcher.

```sh
./run-local.sh aws-emu
```

`spring-boot:test-run` starts Spring Boot with the test classpath, which includes the emulator adapter and profile settings. Open [http://localhost:8080/s3](http://localhost:8080/s3) to see the configured bucket, its total object count, and all object keys. If the bucket has yet to be created, the response shows `bucketExists: false` and a count of `0`. To read a specific text object, open [http://localhost:8080/s3/hello.txt](http://localhost:8080/s3/hello.txt).

Open the [S3 playground](http://localhost:8080/s3-demo.html) to browse keys and save text objects from your browser. The first save creates the demo bucket if needed. Saving an existing key replaces its contents.

`src/test/resources/application-local.properties` sets the demo bucket, emulator HTTPS endpoint, and directory containing the generated credentials and `truststore.p12`. On each S3 operation, Spring Boot checks both files and replaces its S3 client when either changes. Keep Spring Boot running when you restart the emulator. Once the start script finishes restoring, reload the playground. [AWS Java TLS setup](https://docs.aws.amazon.com/emulator/latest/userguide/point-sdk-to-emulator.html#trusting-the-emulator-certificate-in-each-sdk)
