#!/bin/sh
set -eu

python3 "$(dirname "$0")/../s3-state.py" save localstack
docker stop localstack-aws
