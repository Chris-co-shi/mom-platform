#!/usr/bin/env bash
set -Eeuo pipefail

OUTPUT_FILE="${GITHUB_OUTPUT:-/dev/stdout}"
SUMMARY_FILE="${GITHUB_STEP_SUMMARY:-/dev/null}"
MANUAL_SCOPE="${MANUAL_SCOPE:-auto}"
EVENT_NAME="${EVENT_NAME:-local}"
PR_ACTION="${PR_ACTION:-}"
PR_PREVIOUS_SHA="${PR_PREVIOUS_SHA:-}"

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

AUTO_MODE="auto"
case "$EVENT_NAME" in
  pull_request)
    HEAD_SHA="${PR_HEAD_SHA:?PR_HEAD_SHA is required}"
    if [[ "$PR_ACTION" == "synchronize" \
          && -n "$PR_PREVIOUS_SHA" \
          && ! "$PR_PREVIOUS_SHA" =~ ^0+$ ]] \
       && git cat-file -e "${PR_PREVIOUS_SHA}^{commit}" 2>/dev/null; then
      # 长期 PR 后续 push 只验证本次新增差异，避免旧基础设施改动在纯文档提交后反复触发。
      BASE_SHA="$PR_PREVIOUS_SHA"
      AUTO_MODE="auto:pull-request-incremental"
    else
      # PR 首次创建、重新打开或旧 Head 不可达时，使用完整 PR 差异确保首次基础设施验收不遗漏。
      BASE_SHA="${PR_BASE_SHA:?PR_BASE_SHA is required}"
      AUTO_MODE="auto:pull-request-full"
    fi
    ;;
  push)
    BASE_SHA="${PUSH_BASE_SHA:-}"
    HEAD_SHA="${PUSH_HEAD_SHA:-HEAD}"
    if [[ -z "$BASE_SHA" || "$BASE_SHA" =~ ^0+$ ]]; then
      BASE_SHA="$(git rev-parse "${HEAD_SHA}^")"
    fi
    AUTO_MODE="auto:push"
    ;;
  *)
    HEAD_SHA="${PUSH_HEAD_SHA:-HEAD}"
    BASE_SHA="$(git rev-parse "${HEAD_SHA}^")"
    AUTO_MODE="auto:local"
    ;;
esac

git diff --name-only "$BASE_SHA" "$HEAD_SHA" > changed-files.txt

# 只从可能影响运行时的源码、配置、POM 和 CI 脚本中提取新增内容。
# 工程规范、PR 模板和本地 Codex 工具即使提到 Nacos/PostgreSQL/Seata，
# 也不能因此启动重型基础设施验证。
python3 - "$BASE_SHA" "$HEAD_SHA" > changed-additions.diff <<'PY'
from __future__ import annotations

import pathlib
import subprocess
import sys

base, head = sys.argv[1:3]
diff = subprocess.check_output(
    ["git", "diff", "--unified=0", base, head],
    text=True,
    errors="replace",
)

excluded_exact = {
    "AGENTS.md",
    ".github/pull_request_template.md",
    ".github/workflows/ci.yml",
    ".github/scripts/detect-ci-scope.sh",
    ".github/scripts/validate-engineering-baseline.sh",
    "scripts/codex-doctor.sh",
    "scripts/codex-verify-changed.sh",
    "scripts/codex-mvn-test.sh",
    "scripts/summarize-maven-failure.py",
}
relevant_suffixes = {".java", ".xml", ".yml", ".yaml", ".properties", ".sql", ".sh"}


def relevant(path: str | None) -> bool:
    if not path or path == "/dev/null":
        return False
    if path in excluded_exact or path.startswith(("docs/", ".codex/")):
        return False
    if path == "pom.xml" or path.endswith("/pom.xml"):
        return True
    if path.startswith((".github/workflows/", ".github/scripts/")):
        return True
    return pathlib.PurePosixPath(path).suffix.lower() in relevant_suffixes


current: str | None = None
for line in diff.splitlines():
    if line.startswith("+++ b/"):
        current = line[6:]
        continue
    if line.startswith("+++ /dev/null"):
        current = None
        continue
    if line.startswith("+") and not line.startswith("+++") and relevant(current):
        print(line)
PY

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
emit mode "$AUTO_MODE"
emit changed_count "$(wc -l < changed-files.txt | tr -d ' ')"

emit_summary "### Infrastructure scope"
emit_summary ""
emit_summary "- Mode: ${AUTO_MODE}"
emit_summary "- Diff range: ${BASE_SHA}..${HEAD_SHA}"
emit_summary "- Changed files: $(wc -l < changed-files.txt | tr -d ' ')"
emit_summary "- Nacos/Redis: ${nacos_redis}"
emit_summary "- PostgreSQL: ${postgresql}"
emit_summary "- Seata: ${seata}"
