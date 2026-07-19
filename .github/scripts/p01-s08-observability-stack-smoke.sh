#!/usr/bin/env bash
set -Eeuo pipefail

NETWORK="mom-p01-s08-network"
POSTGRES_CONTAINER="mom-p01-s08-postgres"
REDIS_CONTAINER="mom-p01-s08-redis"
TEMPO_CONTAINER="mom-p01-s08-tempo"
COLLECTOR_CONTAINER="mom-p01-s08-otel-collector"
PROMETHEUS_CONTAINER="mom-p01-s08-prometheus"
LOKI_CONTAINER="mom-p01-s08-loki"
ALLOY_CONTAINER="mom-p01-s08-alloy"
GRAFANA_CONTAINER="mom-p01-s08-grafana"
POSTGRES_PORT=5435
MDM_PORT=20230
INTEGRATION_PORT=20830
GATEWAY_PORT=20030
TRACE_ID=55555555555555555555555555555555
PARENT_SPAN_ID=6666666666666666
OUTAGE_TRACE_ID=77777777777777777777777777777777
OUTAGE_PARENT_SPAN_ID=8888888888888888
CORRELATION_ID=p01-s08-correlation-001
LOG_DIR="$(pwd)/p01-s08-app-logs"
MDM_PID=""
INTEGRATION_PID=""
GATEWAY_PID=""

SECURITY_EXCLUSIONS="org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  set +e
  [[ -n "$GATEWAY_PID" ]] && kill "$GATEWAY_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && kill "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  [[ -n "$GATEWAY_PID" ]] && wait "$GATEWAY_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && wait "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && wait "$MDM_PID" 2>/dev/null
  for container in \
    "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" "$TEMPO_CONTAINER" "$COLLECTOR_CONTAINER" \
    "$PROMETHEUS_CONTAINER" "$LOKI_CONTAINER" "$ALLOY_CONTAINER" "$GRAFANA_CONTAINER"; do
    docker logs "$container" > "${container}.log" 2>&1 || true
  done
  docker rm -f \
    "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" "$TEMPO_CONTAINER" "$COLLECTOR_CONTAINER" \
    "$PROMETHEUS_CONTAINER" "$LOKI_CONTAINER" "$ALLOY_CONTAINER" "$GRAFANA_CONTAINER" \
    >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

rm -rf "$LOG_DIR"
mkdir -p "$LOG_DIR"
touch "$LOG_DIR/gateway.log" "$LOG_DIR/mdm.log" "$LOG_DIR/integration.log"
docker rm -f \
  "$POSTGRES_CONTAINER" "$REDIS_CONTAINER" "$TEMPO_CONTAINER" "$COLLECTOR_CONTAINER" \
  "$PROMETHEUS_CONTAINER" "$LOKI_CONTAINER" "$ALLOY_CONTAINER" "$GRAFANA_CONTAINER" \
  >/dev/null 2>&1 || true
docker network rm "$NETWORK" >/dev/null 2>&1 || true
docker network create "$NETWORK" >/dev/null

source .github/scripts/p01-s08-observability-infra.sh
source .github/scripts/p01-s08-observability-apps.sh
source .github/scripts/p01-s08-observability-verify.sh
