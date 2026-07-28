#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT_DIR" || exit 1

failures=0
warnings=0

ok() { printf 'OK    %s\n' "$1"; }
warn() { printf 'WARN  %s\n' "$1"; warnings=$((warnings + 1)); }
fail() { printf 'FAIL  %s\n' "$1"; failures=$((failures + 1)); }

printf 'MOM_CODEX_DOCTOR\n'
printf 'ROOT  %s\n' "$ROOT_DIR"

if command -v python3 >/dev/null 2>&1; then
  ok "Python: $(python3 --version 2>&1)"
else
  fail "python3 command not found"
fi

if command -v java >/dev/null 2>&1; then
  java_line="$(java -version 2>&1 | head -n 1)"
  java_major="$(printf '%s' "$java_line" | sed -E 's/.*version "?([0-9]+).*/\1/')"
  if [[ "$java_major" == "25" ]]; then
    ok "JDK 25: $java_line"
  else
    fail "JDK must be 25, found: $java_line"
  fi
else
  fail "java command not found"
fi

if command -v mvn >/dev/null 2>&1; then
  maven_line="$(mvn -version 2>/dev/null | head -n 1)"
  maven_version="$(printf '%s' "$maven_line" | sed -E 's/.*Apache Maven ([0-9.]+).*/\1/')"
  if command -v python3 >/dev/null 2>&1 && python3 - "$maven_version" <<'PY'
import sys
parts = tuple(int(x) for x in sys.argv[1].split('.') if x.isdigit())
raise SystemExit(0 if parts >= (3, 9, 9) else 1)
PY
  then
    ok "Maven >= 3.9.9: $maven_line"
  else
    fail "Maven must be >= 3.9.9, found: $maven_line"
  fi
else
  fail "mvn command not found"
fi

branch="$(git branch --show-current 2>/dev/null || true)"
[[ -n "$branch" ]] && ok "Git branch: $branch" || warn "Detached HEAD"

changed_count="$({ git diff --name-only; git diff --cached --name-only; git ls-files --others --exclude-standard; } | sort -u | sed '/^$/d' | wc -l | tr -d ' ')"
if [[ "$changed_count" == "0" ]]; then
  ok "Working tree clean"
else
  warn "Working tree has ${changed_count} changed/untracked files"
fi

if git rev-parse --verify origin/main >/dev/null 2>&1; then
  ahead="$(git rev-list --count origin/main..HEAD 2>/dev/null || echo '?')"
  behind="$(git rev-list --count HEAD..origin/main 2>/dev/null || echo '?')"
  ok "Against origin/main: ahead=${ahead}, behind=${behind}"
else
  warn "origin/main not available; run git fetch before changed-scope verification"
fi

if command -v docker >/dev/null 2>&1; then
  if docker info >/dev/null 2>&1; then
    ok "Docker available (optional for normal unit tests)"
  else
    warn "Docker CLI found but daemon unavailable; only explicit integration tests need it"
  fi
else
  warn "Docker not installed; normal unit tests remain available"
fi

if bash .github/scripts/validate-engineering-baseline.sh >/tmp/mom-baseline-doctor.log 2>&1; then
  ok "Engineering baseline"
else
  fail "Engineering baseline; run: bash .github/scripts/validate-engineering-baseline.sh"
fi
rm -f /tmp/mom-baseline-doctor.log

printf 'RESULT failures=%d warnings=%d\n' "$failures" "$warnings"
exit "$failures"
