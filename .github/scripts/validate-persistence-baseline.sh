#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
python3 "$ROOT_DIR/.github/scripts/validate_persistence_baseline.py" --root "$ROOT_DIR" "$@"
exec python3 "$ROOT_DIR/.github/scripts/validate_java_persistence_baseline.py" --root "$ROOT_DIR" "$@"
