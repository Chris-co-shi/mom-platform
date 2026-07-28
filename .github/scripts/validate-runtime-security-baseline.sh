#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
exec python3 "$ROOT_DIR/.github/scripts/validate_runtime_security_baseline.py" --root "$ROOT_DIR" "$@"
