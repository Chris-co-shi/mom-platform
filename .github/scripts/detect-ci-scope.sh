#!/usr/bin/env bash
set -Eeuo pipefail

OUTPUT_FILE="${GITHUB_OUTPUT:-/dev/stdout}"
SUMMARY_FILE="${GITHUB_STEP_SUMMARY:-/dev/null}"
MANUAL_SCOPE="${MANUAL_SCOPE:-auto}"
EVENT_NAME="${EVENT_NAME:-local}"

emit() {
  printf '%s=%s\n' "$1" "$2" >>"$OUTPUT_FILE"
}

emit_summary() {
  printf '%s\n' "$1" >>"$SUMMARY_FILE"
}

set_manual_scope() {
  local scope="$1"
  local nacos_redis=false
  local postgresql=false
  local seata=false

  case "$scope" in
    all)
      nacos_redis=true
      postgresql=true
      seata=true
      ;;
    none)
      ;;
    nacos-redis)
      nacos_redis=true
      ;;
    postgresql)
      postgresql=true
      ;;
    seata)
      seata=true
      ;;
    auto)
      return 1
      ;;
    *)
      echo "Unsupported infrastructure scope: $scope" >&2
      exit 2
      ;;
  esac

  emit nacos_redis "$nacos_redis"
  emit postgresql "$postgresql"
  emit seata "$seata"
  emit mode "manual:${scope}"
  emit_summary "### Infrastructure scope"
  emit_summary ""
  emit_summary "- Mode: manual:${scope}"
  emit_summary "- Nacos/Redis: ${nacos_redis}"
  emit_summary "- PostgreSQL: ${postgresql}"
  emit_summary "- Seata: ${seata}"
  return 0
}

if set_manual_scope "$MANUAL_SCOPE"; then
  exit 0
fi

case "$EVENT_NAME" in
  pull_request)
    BASE_SHA="${PR_BASE_SHA:?PR_BASE_SHA is required}"
    HEAD_SHA="${PR_HEAD_SHA:?PR_HEAD_SHA is required}"
    ;;
  push)
    BASE_SHA="${PUSH_BASE_SHA:-}"
    HEAD_SHA="${PUSH_HEAD_SHA:-HEAD}"
    if [[ -z "$BASE_SHA" || "$BASE_SHA" =~ ^0+$ ]]; then
      BASE_SHA="$(git rev-parse "${HEAD_SHA}^")"
    fi
    ;;
  *)
    HEAD_SHA="${PUSH_HEAD_SHA:-HEAD}"
    BASE_SHA="$(git rev-parse "${HEAD_SHA}^")"
    ;;
esac

git diff --name-only "$BASE_SHA" "$HEAD_SHA" > changed-files.txt
git diff --unified=0 "$BASE_SHA" "$HEAD_SHA" > changed-content.diff
grep '^+' changed-content.diff | grep -v '^+++' > changed-additions.diff || true

path_matches() {
  local pattern="$1"
  grep --extended-regexp --quiet "$pattern" changed-files.txt
}

content_matches() {
  local pattern="$1"
  grep --extended-regexp --ignore-case --quiet "$pattern" changed-additions.diff
}

nacos_redis=false
postgresql=false
seata=false

if path_matches '(^\.github/scripts/nacos-redis-smoke\.sh$|^mom-gateway/|^mom-integration-platform/|^mom-framework/(mom-openfeign|mom-idempotency|mom-rate-limit)/|/nacos/|/redis/)'; then
  nacos_redis=true
fi
if content_matches '(spring-cloud-starter-alibaba-nacos|nacos-client|spring\.cloud\.nacos|DiscoveryClient|LoadBalanced|RedisTemplate|ReactiveRedis|idempotenc|rate.?limit)'; then
  nacos_redis=true
fi

if path_matches '(^\.github/scripts/p01-s04-postgresql-smoke\.sh$|^mom-framework/mom-data/|^mom-mdm-platform/mom-mdm-server/src/main/resources/db/|/(mapper|repository|persistence)/|\.sql$)'; then
  postgresql=true
fi
if content_matches '(postgresql|flyway|jdbc:postgresql|DataSource|SqlSession|BaseMapper|@Mapper|timestamptz)'; then
  postgresql=true
fi

if path_matches '(^mom-framework/mom-seata/|/seata/|seata[^/]*\.(yml|yaml|properties|sql)$|undo_log)'; then
  seata=true
fi
if content_matches '(spring-cloud-starter-alibaba-seata|@GlobalTransactional|GlobalTransactional|DataSourceProxy|RootContext|undo_log|tx-service-group|service\.vgroupMapping|XID)'; then
  seata=true
fi

emit nacos_redis "$nacos_redis"
emit postgresql "$postgresql"
emit seata "$seata"
emit mode "auto"
emit changed_count "$(wc -l < changed-files.txt | tr -d ' ')"

emit_summary "### Infrastructure scope"
emit_summary ""
emit_summary "- Mode: auto"
emit_summary "- Changed files: $(wc -l < changed-files.txt | tr -d ' ')"
emit_summary "- Nacos/Redis: ${nacos_redis}"
emit_summary "- PostgreSQL: ${postgresql}"
emit_summary "- Seata: ${seata}"
