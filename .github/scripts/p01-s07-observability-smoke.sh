#!/usr/bin/env bash
set -Eeuo pipefail

NETWORK=mom-p01-s07-network
NACOS_CONTAINER=mom-p01-s07-nacos
REDIS_CONTAINER=mom-p01-s07-redis
TEMPO_CONTAINER=mom-p01-s07-tempo
COLLECTOR_CONTAINER=mom-p01-s07-otel-collector
MDM_PORT=20220
INTEGRATION_PORT=20820
GATEWAY_PORT=20020
TRACE_ID=11111111111111111111111111111111
PARENT_SPAN_ID=2222222222222222
OUTAGE_TRACE_ID=33333333333333333333333333333333
OUTAGE_PARENT_SPAN_ID=4444444444444444
CORRELATION_ID=p01-s07-correlation-001
MDM_PID=""
INTEGRATION_PID=""
GATEWAY_PID=""

BOOTSTRAP_EXCLUSIONS="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  set +e
  [[ -n "$GATEWAY_PID" ]] && kill "$GATEWAY_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && kill "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  [[ -n "$GATEWAY_PID" ]] && wait "$GATEWAY_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && wait "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && wait "$MDM_PID" 2>/dev/null
  docker logs "$NACOS_CONTAINER" > p01-s07-nacos.log 2>&1
  docker logs "$REDIS_CONTAINER" > p01-s07-redis.log 2>&1
  docker logs "$TEMPO_CONTAINER" > p01-s07-tempo.log 2>&1
  docker logs "$COLLECTOR_CONTAINER" > p01-s07-collector.log 2>&1
  docker rm -f "$NACOS_CONTAINER" "$REDIS_CONTAINER" "$TEMPO_CONTAINER" "$COLLECTOR_CONTAINER" >/dev/null 2>&1
  docker network rm "$NETWORK" >/dev/null 2>&1
}
trap cleanup EXIT

docker rm -f "$NACOS_CONTAINER" "$REDIS_CONTAINER" "$TEMPO_CONTAINER" "$COLLECTOR_CONTAINER" >/dev/null 2>&1 || true
docker network rm "$NETWORK" >/dev/null 2>&1 || true
docker network create "$NETWORK" >/dev/null

docker run --name "$NACOS_CONTAINER" \
  -e MODE=standalone \
  -e NACOS_AUTH_ENABLE=false \
  -e NACOS_AUTH_TOKEN=TU9NLVBsYXRmb3JtLUNJLU5hY29zLVRva2VuLTIwMjYtMDctMTgtU3Ryb25nLUtleQ== \
  -e NACOS_AUTH_IDENTITY_KEY=mom-platform-ci-key \
  -e NACOS_AUTH_IDENTITY_VALUE=mom-platform-ci-value \
  -e JVM_XMS=256m \
  -e JVM_XMX=256m \
  -e JVM_XMN=128m \
  -p 8848:8848 \
  -p 9848:9848 \
  -d nacos/nacos-server:v3.1.0

docker run --name "$REDIS_CONTAINER" \
  -p 6379:6379 \
  -d redis:8.4.4-alpine \
  redis-server --save "" --appendonly no

docker run --name "$TEMPO_CONTAINER" \
  --network "$NETWORK" \
  -v "$PWD/.github/observability/tempo-config.yml:/etc/tempo.yml:ro" \
  -p 3200:3200 \
  -d grafana/tempo:2.10.5 \
  -config.file=/etc/tempo.yml

docker run --name "$COLLECTOR_CONTAINER" \
  --network "$NETWORK" \
  -v "$PWD/.github/observability/otel-collector-config.yml:/etc/otelcol-contrib/config.yaml:ro" \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 13133:13133 \
  -d otel/opentelemetry-collector-contrib:0.156.0 \
  --config=/etc/otelcol-contrib/config.yaml

for attempt in {1..90}; do
  nacos_ready=false
  redis_ready=false
  tempo_ready=false
  collector_ready=false
  if curl --fail --silent http://127.0.0.1:8848/nacos/v3/admin/core/state/readiness >/dev/null; then
    nacos_ready=true
  fi
  if docker exec "$REDIS_CONTAINER" redis-cli ping | grep --quiet PONG; then
    redis_ready=true
  fi
  if curl --fail --silent http://127.0.0.1:3200/ready >/dev/null; then
    tempo_ready=true
  fi
  if curl --fail --silent http://127.0.0.1:13133/ >/dev/null; then
    collector_ready=true
  fi
  if [[ "$nacos_ready" == "true" && "$redis_ready" == "true" && "$tempo_ready" == "true" && "$collector_ready" == "true" ]]; then
    break
  fi
  if [[ "$attempt" == "90" ]]; then
    echo "Observability infrastructure did not become ready"
    exit 1
  fi
  sleep 2
done

COMMON_TRACE_ENV=(
  MOM_ENVIRONMENT=ci
  TRACING_SAMPLING_PROBABILITY=1.0
  OTEL_TRACING_EXPORT_ENABLED=true
  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://127.0.0.1:4318/v1/traces
  OTEL_EXPORTER_OTLP_TRACES_TIMEOUT=2s
)

env "${COMMON_TRACE_ENV[@]}" \
  java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port=$MDM_PORT \
  --spring.application.name=mom-mdm-server \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  --seata.enabled=false \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > p01-s07-mdm.log 2>&1 &
MDM_PID=$!

env "${COMMON_TRACE_ENV[@]}" REDIS_HOST=127.0.0.1 REDIS_PORT=6379 \
  java -jar mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port=$INTEGRATION_PORT \
  --spring.application.name=mom-integration-server \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  --seata.enabled=false \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > p01-s07-integration.log 2>&1 &
INTEGRATION_PID=$!

env "${COMMON_TRACE_ENV[@]}" REDIS_HOST=127.0.0.1 REDIS_PORT=6379 \
  GATEWAY_RATE_LIMIT_REPLENISH_RATE=100 \
  GATEWAY_RATE_LIMIT_BURST_CAPACITY=100 \
  GATEWAY_RATE_LIMIT_REQUESTED_TOKENS=1 \
  java -jar mom-gateway/target/mom-gateway-0.1.0-SNAPSHOT-exec.jar \
  --server.port=$GATEWAY_PORT \
  --spring.application.name=mom-gateway \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  > p01-s07-gateway.log 2>&1 &
GATEWAY_PID=$!

for attempt in {1..90}; do
  status=$(curl --silent --output p01-s07-http-response.json \
    --dump-header p01-s07-http-response-headers.txt \
    --write-out '%{http_code}' \
    --header "X-Correlation-Id: $CORRELATION_ID" \
    --header "traceparent: 00-$TRACE_ID-$PARENT_SPAN_ID-01" \
    http://127.0.0.1:$GATEWAY_PORT/api/integration/mdm-probe || true)
  if [[ "$status" == "200" ]]; then
    break
  fi
  if [[ "$attempt" == "90" ]]; then
    echo "Gateway tracing request failed with HTTP $status"
    cat p01-s07-http-response.json || true
    exit 1
  fi
  sleep 2
done

jq --exit-status --arg traceId "$TRACE_ID" --arg correlationId "$CORRELATION_ID" '
  .correlationId == $correlationId
  and .mdmCorrelationId == $correlationId
  and .traceId == $traceId
  and .mdmTraceId == $traceId
  and (.spanId | length) == 16
  and (.mdmSpanId | length) == 16
  and .spanId != .mdmSpanId
' p01-s07-http-response.json

grep --quiet "trace_id=$TRACE_ID" p01-s07-integration.log
grep --quiet "trace_id=$TRACE_ID" p01-s07-mdm.log

for attempt in {1..60}; do
  tempo_status=$(curl --silent --output p01-s07-tempo-trace.json \
    --write-out '%{http_code}' \
    --header 'Accept: application/json' \
    http://127.0.0.1:3200/api/traces/$TRACE_ID || true)
  if [[ "$tempo_status" == "200" ]] \
    && grep --quiet 'mom-gateway' p01-s07-tempo-trace.json \
    && grep --quiet 'mom-integration-server' p01-s07-tempo-trace.json \
    && grep --quiet 'mom-mdm-server' p01-s07-tempo-trace.json; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    echo "Tempo did not contain the complete Gateway -> Integration -> MDM trace"
    cat p01-s07-tempo-trace.json || true
    exit 1
  fi
  sleep 1
done

docker stop "$COLLECTOR_CONTAINER" >/dev/null

outage_status=$(curl --silent --output p01-s07-collector-outage-response.json \
  --write-out '%{http_code}' \
  --header "X-Correlation-Id: p01-s07-collector-outage" \
  --header "traceparent: 00-$OUTAGE_TRACE_ID-$OUTAGE_PARENT_SPAN_ID-01" \
  http://127.0.0.1:$GATEWAY_PORT/api/integration/mdm-probe || true)
[[ "$outage_status" == "200" ]]
jq --exit-status --arg traceId "$OUTAGE_TRACE_ID" '
  .traceId == $traceId
  and .mdmTraceId == $traceId
' p01-s07-collector-outage-response.json

printf 'httpTrace=%s\ncollectorOutage=%s\n' "$status" "$outage_status" > p01-s07-status.txt
