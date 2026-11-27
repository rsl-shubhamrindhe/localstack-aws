#!/bin/sh
set -eu

python3 "$(dirname "$0")/../s3-state.py" save aws-emu
docker stop aws-emulator
