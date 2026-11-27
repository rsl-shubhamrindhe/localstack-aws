#!/bin/sh
set -eu

case "${1:-aws-emu}" in
  aws-emu)
    SPRING_PROFILES_ACTIVE=local
    ;;
  localstack)
    SPRING_PROFILES_ACTIVE=local,localstack
    ;;
  *)
    printf 'Usage: %s [aws-emu|localstack]\n' "$0" >&2
    exit 2
    ;;
esac

export SPRING_PROFILES_ACTIVE
unset LOCALSTACK_AUTH_TOKEN
cd "$(dirname "$0")"
exec ./mvnw spring-boot:test-run
