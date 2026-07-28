#!/usr/bin/env bash
set -Eeuo pipefail

OUTPUT_FILE="${GITHUB_OUTPUT:-/dev/stdout}"
SUMMARY_FILE="${GITHUB_STEP_SUMMARY:-/dev/null}"
MANUAL_SCOPE="${MANUAL_SCOPE:-auto}"
EVENT_NAME="${EVENT_NAME:-local}"
PR_ACTION="${PR_ACTION:-}"
PR_PREVIOUS_SHA="${PR_PREVIOUS_SHA:-}"

emit() { printf '%s=%s\n' "$1" "$2" >> "$OUTPUT_FILE"; }
emit_summary() { printf '%s\n' "$1" >> "$SUMMARY_FILE"; }

emit_scopes() {
  emit nacos "$nacos"
  emit redis_idempotency "$redis_idempotency"
  emit redis_rate_limit "$redis_rate_limit"
  emit postgresql "$postgresql"
  emit messaging "$messaging"
  emit seata "$seata"
  emit observability "$observability"
}

set_manual_scope() {
  local scope="$1"
  nacos=false
  redis_idempotency=false
  redis_rate_limit=false
  postgresql=false
  messaging=false
  seata=false
  observability=false

  case "$scope" in
    all)
      nacos=true
      redis_idempotency=true
      redis_rate_limit=true
      postgresql=true
      messaging=true
      seata=true
      observability=true
      ;;
    none) ;;
    nacos) nacos=true ;;
    redis) redis_idempotency=true; redis_rate_limit=true ;;
    postgresql) postgresql=true ;;
    messaging) messaging=true ;;
    seata) seata=true ;;
    observability) observability=true ;;
    auto) return 1 ;;
    *) echo "Unsupported infrastructure scope: $scope" >&2; exit 2 ;;
  esac

  emit_scopes
  emit mode "manual:${scope}"
  emit changed_count 0
  emit_scope_summary "manual:${scope}" "manual"
  return 0
}

emit_scope_summary() {
  local mode="$1"
  local range="$2"
  emit_summary "### Infrastructure scope"
  emit_summary ""
  emit_summary "- Mode: ${mode}"
  emit_summary "- Diff range: ${range}"
  emit_summary "- Nacos: ${nacos}"
  emit_summary "- Redis Idempotency: ${redis_idempotency}"
  emit_summary "- Redis Rate Limit: ${redis_rate_limit}"
  emit_summary "- PostgreSQL: ${postgresql}"
  emit_summary "- Messaging: ${messaging}"
  emit_summary "- Seata: ${seata}"
  emit_summary "- Observability: ${observability}"
}

if set_manual_scope "$MANUAL_SCOPE"; then exit 0; fi

AUTO_MODE="auto"
case "$EVENT_NAME" in
  pull_request)
    HEAD_SHA="${PR_HEAD_SHA:?PR_HEAD_SHA is required}"
    if [[ "$PR_ACTION" == "synchronize" && -n "$PR_PREVIOUS_SHA" && ! "$PR_PREVIOUS_SHA" =~ ^0+$ ]] \
      && git merge-base --is-ancestor "$PR_PREVIOUS_SHA" "$HEAD_SHA" 2>/dev/null; then
      BASE_SHA="$PR_PREVIOUS_SHA"
      AUTO_MODE="auto:pull-request-incremental"
    else
      BASE_SHA="${PR_BASE_SHA:?PR_BASE_SHA is required}"
      AUTO_MODE="auto:pull-request-full"
    fi
    ;;
  push)
    HEAD_SHA="${PUSH_HEAD_SHA:-HEAD}"
    BASE_SHA="${PUSH_BASE_SHA:-}"
    if [[ -z "$BASE_SHA" || "$BASE_SHA" =~ ^0+$ ]] || ! git cat-file -e "${BASE_SHA}^{commit}" 2>/dev/null; then
      BASE_SHA="$(git rev-list --max-parents=0 "$HEAD_SHA" | tail -n 1)"
      AUTO_MODE="auto:push-full"
    else
      AUTO_MODE="auto:push"
    fi
    ;;
  *)
    HEAD_SHA="${PUSH_HEAD_SHA:-HEAD}"
    BASE_SHA="${PUSH_BASE_SHA:-$(git rev-parse "${HEAD_SHA}^")}"
    AUTO_MODE="auto:local"
    ;;
esac

git diff --name-only "$BASE_SHA" "$HEAD_SHA" > changed-files.txt

path_matches() { grep --extended-regexp --quiet "$1" changed-files.txt; }

nacos=false
redis_idempotency=false
redis_rate_limit=false
postgresql=false
messaging=false
seata=false
observability=false

# Scope detector 或主 CI 自身变化必须验证所有主 CI 基础设施分支，避免脚本修改跳过自身。
if path_matches '(^\.github/scripts/detect-ci-scope\.sh$|^\.github/workflows/ci\.yml$)'; then
  nacos=true
  redis_idempotency=true
  redis_rate_limit=true
  postgresql=true
  seata=true
fi

if path_matches '(^\.github/scripts/nacos-discovery-smoke\.sh$|^mom-gateway/|^mom-(mdm|integration)-platform/.*/(client|.*ServiceProbe)|/nacos/)'; then
  nacos=true
fi
if path_matches '(^\.github/scripts/redis-idempotency-smoke\.sh$|^mom-framework/mom-idempotency/|IntegrationIdempotencyProbeController|/idempotenc)'; then
  redis_idempotency=true
fi
if path_matches '(^\.github/scripts/redis-rate-limit-smoke\.sh$|^mom-framework/mom-rate-limit/|^mom-gateway/|/rate.?limit|RedisRate)'; then
  redis_rate_limit=true
fi
if path_matches '(^\.github/scripts/p01-s04-postgresql-smoke\.sh$|^mom-framework/(mom-data|mom-outbox)/|^mom-(mdm|integration)-platform/.*/src/(main|test)/resources/db/|/(mapper|repository|persistence)/|\.sql$)'; then
  postgresql=true
fi
if path_matches '(^\.github/workflows/messaging-ci\.yml$|^\.github/scripts/p01-s05-rocketmq-outbox-smoke\.sh$|^mom-framework/(mom-messaging|mom-outbox)/|/messaging/|rocketmq|outbox|inbox)'; then
  messaging=true
fi
if path_matches '(^\.github/workflows/seata-ci\.yml$|^\.github/scripts/p01-s06-seata-at-smoke\.sh$|^mom-framework/mom-seata/|/seata/|undo_log)'; then
  seata=true
fi
if path_matches '(^\.github/workflows/observability(-stack)?-ci\.yml$|^\.github/scripts/.*observability.*smoke\.sh$|^mom-framework/(mom-observation|mom-tracing|mom-metrics|mom-logging)/|/observability/|/tracing/)'; then
  observability=true
fi

emit_scopes
emit mode "$AUTO_MODE"
emit changed_count "$(wc -l < changed-files.txt | tr -d ' ')"
emit_scope_summary "$AUTO_MODE" "${BASE_SHA}..${HEAD_SHA}"
