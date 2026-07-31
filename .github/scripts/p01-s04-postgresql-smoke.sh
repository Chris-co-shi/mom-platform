#!/usr/bin/env bash
set -Eeuo pipefail
# Compatibility entry point retained for existing CI wiring; the Phase 01 CRUD probe has been retired.
exec bash .github/scripts/mdm-postgresql-smoke.sh "$@"
