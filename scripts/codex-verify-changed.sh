#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT_DIR"

BASE_REF="${BASE_REF:-origin/main}"
VERIFY_LEVEL="${CODEX_VERIFY_LEVEL:-test}"
SPECIFIED_TEST="${CODEX_TEST:-}"

case "$VERIFY_LEVEL" in
  compile) goal=test-compile ;;
  test) goal=test ;;
  verify) goal=verify ;;
  *) echo "Unsupported CODEX_VERIFY_LEVEL: $VERIFY_LEVEL (compile|test|verify)" >&2; exit 2 ;;
esac

if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  if git rev-parse --verify main >/dev/null 2>&1; then
    BASE_REF=main
  elif git rev-parse --verify HEAD^ >/dev/null 2>&1; then
    BASE_REF=HEAD^
  else
    BASE_REF=HEAD
  fi
fi

changed_file_list="$(mktemp)"
module_file="$(mktemp)"
trap 'rm -f "$changed_file_list" "$module_file"' EXIT
{
  git diff --name-only "$BASE_REF"...HEAD 2>/dev/null || true
  git diff --name-only 2>/dev/null || true
  git diff --cached --name-only 2>/dev/null || true
  git ls-files --others --exclude-standard 2>/dev/null || true
} | sort -u | sed '/^$/d' >"$changed_file_list"

changed_count="$(wc -l <"$changed_file_list" | tr -d ' ')"
printf 'CODEX_CHANGED_VERIFY base=%s level=%s files=%s\n' "$BASE_REF" "$VERIFY_LEVEL" "$changed_count"

bash .github/scripts/validate-engineering-baseline.sh

if [[ "$changed_count" == "0" ]]; then
  echo "No changed files; Maven verification skipped."
  exit 0
fi

full_reactor=false
while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  case "$file" in
    pom.xml|mom-dependencies/*|.mvn/*)
      full_reactor=true
      ;;
  esac

  dir="$(dirname "$file")"
  [[ "$dir" == "." ]] && dir=""
  while [[ -n "$dir" && "$dir" != "." ]]; do
    if [[ -f "$dir/pom.xml" ]]; then
      printf '%s\n' "$dir" >>"$module_file"
      break
    fi
    dir="$(dirname "$dir")"
    [[ "$dir" == "." ]] && dir=""
  done
done <"$changed_file_list"

sort -u "$module_file" -o "$module_file"
module_count="$(sed '/^$/d' "$module_file" | wc -l | tr -d ' ')"

maven_args=()
if [[ "$full_reactor" == "true" ]]; then
  echo "Scope: full reactor (root POM, Maven baseline or dependency management changed)."
elif [[ "$module_count" -gt 0 ]]; then
  module_csv="$(paste -sd, "$module_file")"
  echo "Scope modules: $module_csv"
  maven_args+=("-pl" "$module_csv" "-am")
else
  echo "Only documentation or non-Maven files changed; Maven verification skipped."
  exit 0
fi

if [[ -n "$SPECIFIED_TEST" ]]; then
  maven_args+=("-Dtest=$SPECIFIED_TEST" "-Dsurefire.failIfNoSpecifiedTests=false")
fi
maven_args+=("$goal")

bash scripts/codex-mvn-test.sh "${maven_args[@]}"
