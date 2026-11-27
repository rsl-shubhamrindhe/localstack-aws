#!/bin/sh
set -eu

mkdir -p "$HOME/.aws/emulator"

docker run -d --rm --name aws-emulator \
  --memory=2g --memory-swap=2g \
  -p 127.0.0.1:9297:9297 \
  -e "HOME=$HOME" \
  -v "$HOME/.aws/emulator:$HOME/.aws/emulator" \
  -v localstack-aws-aws-emu-s3-state:/s3-state \
  aws-emulator --port 9297 --telemetry off

python3 "$(dirname "$0")/../s3-state.py" restore aws-emu
