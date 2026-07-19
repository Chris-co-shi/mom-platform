#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_SCRIPT=.github/scripts/p01-s08-observability-stack-smoke.sh
TEMP_SCRIPT=$(mktemp)
COLLECTOR_CONTAINER=mom-p01-s08-otel-collector
NETWORK=mom-p01-s08-network

cleanup() {
  set +e
  docker logs "$COLLECTOR_CONTAINER" > "${COLLECTOR_CONTAINER}.log" 2>&1 || true
  docker rm -f "$COLLECTOR_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
  rm -f "$TEMP_SCRIPT"
}
trap cleanup EXIT

# P01-S07 与 P01-S08 使用不同的 Tempo 容器名；保持各自 Collector 配置独立，
# 避免为了 CI 便利修改已经验收通过的 P01-S07 路由。
sed 's#\.github/observability/otel-collector-config.yml#.github/observability/otel-collector-stack-config.yml#' \
  "$SOURCE_SCRIPT" > "$TEMP_SCRIPT"
chmod +x "$TEMP_SCRIPT"
bash "$TEMP_SCRIPT"
