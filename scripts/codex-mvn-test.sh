#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
RUNTIME_DIR="${ROOT_DIR}/.codex/runtime"
LOG_DIR="${RUNTIME_DIR}/logs"
SUMMARY_DIR="${RUNTIME_DIR}/summaries"
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')-$$"
LOG_FILE="${LOG_DIR}/maven-${TIMESTAMP}.log"
SUMMARY_FILE="${SUMMARY_DIR}/maven-${TIMESTAMP}.txt"
MAVEN_BIN="${MAVEN_BIN:-mvn}"

mkdir -p "$LOG_DIR" "$SUMMARY_DIR"

if [[ "$#" -eq 0 ]]; then
  set -- test
fi

set +e
"$MAVEN_BIN" -B -ntp -Dstyle.color=never "$@" >"$LOG_FILE" 2>&1
STATUS=$?
set -e

if [[ "$STATUS" -eq 0 ]]; then
  {
    echo "MAVEN_RESULT: SUCCEEDED"
    echo "EXIT_CODE: 0"
    echo "COMMAND: ${MAVEN_BIN} -B -ntp -Dstyle.color=never $*"
    echo "FULL_LOG: ${LOG_FILE#"$ROOT_DIR"/}"
  } | tee "$SUMMARY_FILE"
  exit 0
fi

python3 "$ROOT_DIR/scripts/summarize-maven-failure.py" \
  --root "$ROOT_DIR" \
  --log "$LOG_FILE" \
  --exit-code "$STATUS" \
  --command "${MAVEN_BIN} -B -ntp -Dstyle.color=never $*" \
  | tee "$SUMMARY_FILE"

exit "$STATUS"
