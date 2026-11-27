#!/bin/bash
set -eu

if [ -z "${LOCALSTACK_AUTH_TOKEN:-}" ] && [ -t 0 ]; then
  read -r -s -p 'LocalStack developer token: ' LOCALSTACK_AUTH_TOKEN
  printf '\n' >&2
fi

: "${LOCALSTACK_AUTH_TOKEN:?Set LOCALSTACK_AUTH_TOKEN or run this script from a terminal to enter your token}"
export LOCALSTACK_AUTH_TOKEN

docker run -d --rm --name localstack-aws \
  -p 127.0.0.1:4566:4566 \
  -e LOCALSTACK_AUTH_TOKEN \
  -v localstack-aws-localstack-s3-state:/s3-state \
  localstack/localstack

python3 "$(dirname "$0")/../s3-state.py" restore localstack
