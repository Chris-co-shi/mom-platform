#!/usr/bin/env bash

# 该文件由主 Smoke 脚本 source。服务发现已在主 CI 验证，此处通过受控 URL 覆盖聚焦可观测数据面。
COMMON_TRACE_ENV=(
  "MOM_ENVIRONMENT=ci"
  "TRACING_SAMPLING_PROBABILITY=1.0"
  "OTEL_TRACING_EXPORT_ENABLED=true"
  "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://127.0.0.1:4318/v1/traces"
  "OTEL_EXPORTER_OTLP_TRACES_TIMEOUT=2000"
  "JAVA_TOOL_OPTIONS=-Xms96m -Xmx256m -XX:MaxMetaspaceSize=160m"
)

COMMON_LOG_ARGS=(
  --logging.structured.format.console=logstash
  --logging.structured.json.rename.traceId=trace_id
  --logging.structured.json.rename.spanId=span_id
  --management.metrics.distribution.percentiles-histogram.http.server.requests=true
)

env "${COMMON_TRACE_ENV[@]}" \
  POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$POSTGRES_PORT" POSTGRES_DATABASE=mom_platform \
  POSTGRES_SCHEMA=mom_mdm POSTGRES_USERNAME=mom POSTGRES_PASSWORD=mom \
  NACOS_DISCOVERY_ENABLED=false \
  java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$MDM_PORT" \
  --spring.application.name=mom-mdm-server \
  --seata.enabled=false \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  --logging.structured.json.add.service=mom-mdm-server \
  --logging.structured.json.add.environment=ci \
  "${COMMON_LOG_ARGS[@]}" \
  > "$LOG_DIR/mdm.log" 2>&1 &
MDM_PID=$!

env "${COMMON_TRACE_ENV[@]}" \
  POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$POSTGRES_PORT" POSTGRES_DATABASE=mom_platform \
  POSTGRES_SCHEMA=mom_integration POSTGRES_USERNAME=mom POSTGRES_PASSWORD=mom \
  REDIS_HOST=127.0.0.1 REDIS_PORT=6379 \
  NACOS_DISCOVERY_ENABLED=false \
  java -jar mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$INTEGRATION_PORT" \
  --spring.application.name=mom-integration-server \
  --spring.cloud.openfeign.client.config.mom-mdm-server.url="http://127.0.0.1:${MDM_PORT}" \
  --seata.enabled=false \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  --logging.structured.json.add.service=mom-integration-server \
  --logging.structured.json.add.environment=ci \
  "${COMMON_LOG_ARGS[@]}" \
  > "$LOG_DIR/integration.log" 2>&1 &
INTEGRATION_PID=$!

env "${COMMON_TRACE_ENV[@]}" \
  REDIS_HOST=127.0.0.1 REDIS_PORT=6379 \
  NACOS_DISCOVERY_ENABLED=false \
  GATEWAY_RATE_LIMIT_REPLENISH_RATE=1 \
  GATEWAY_RATE_LIMIT_BURST_CAPACITY=3 \
  GATEWAY_RATE_LIMIT_REQUESTED_TOKENS=3 \
  java -jar mom-gateway/target/mom-gateway-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$GATEWAY_PORT" \
  --spring.application.name=mom-gateway \
  --spring.cloud.gateway.server.webflux.routes[0].uri="http://127.0.0.1:${INTEGRATION_PORT}" \
  --logging.structured.json.add.service=mom-gateway \
  --logging.structured.json.add.environment=ci \
  "${COMMON_LOG_ARGS[@]}" \
  > "$LOG_DIR/gateway.log" 2>&1 &
GATEWAY_PID=$!

wait_for_http_200() {
  local url=$1
  local output=$2
  for attempt in {1..120}; do
    status=$(curl --silent --output "$output" --write-out '%{http_code}' "$url" || true)
    if [[ "$status" == 200 ]]; then
      return 0
    fi
    if [[ "$attempt" == 120 ]]; then
      echo "Application did not become healthy: $url HTTP $status"
      cat "$output" || true
      return 1
    fi
    sleep 2
  done
}

wait_for_http_200 "http://127.0.0.1:${MDM_PORT}/actuator/health" p01-s08-mdm-health.json
wait_for_http_200 "http://127.0.0.1:${INTEGRATION_PORT}/actuator/health" p01-s08-integration-health.json
wait_for_http_200 "http://127.0.0.1:${GATEWAY_PORT}/actuator/health" p01-s08-gateway-health.json

success_status=$(curl --silent --output p01-s08-success-response.json \
  --write-out '%{http_code}' \
  --header "X-Correlation-Id: $CORRELATION_ID" \
  --header "traceparent: 00-$TRACE_ID-$PARENT_SPAN_ID-01" \
  "http://127.0.0.1:${GATEWAY_PORT}/api/integration/mdm-probe")
[[ "$success_status" == 200 ]]
jq --exit-status --arg traceId "$TRACE_ID" --arg correlationId "$CORRELATION_ID" '
  .correlationId == $correlationId
  and .mdmCorrelationId == $correlationId
  and .traceId == $traceId
  and .mdmTraceId == $traceId
  and .spanId != .mdmSpanId
' p01-s08-success-response.json >/dev/null

rejected_status=$(curl --silent --output p01-s08-rate-limit-response.json \
  --write-out '%{http_code}' \
  "http://127.0.0.1:${GATEWAY_PORT}/api/integration/mdm-probe")
[[ "$rejected_status" == 429 ]]
