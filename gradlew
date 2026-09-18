#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/backend/gradlew" -p "$DIR/backend" "$@"
