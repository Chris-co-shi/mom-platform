#!/usr/bin/env bash
set -Eeuo pipefail

NETWORK=mom-p01-s08-network
NACOS_CONTAINER=mom-p01-s08-nacos
REDIS_CONTAINER=mom-p01-s08-redis
TEMPO_CONTAINER=mom-p01-s08-tempo
LOKI_CONTAINER=mom-p01-s08-loki
PROMETHEUS_CONTAINER=mom-p01-s08-prometheus
ALLOY_CONTAINER=mom-p01-s08-alloy
GRAFANA_CONTAINER=mom-p01-s08-grafana
MDM_PORT=20230
INTEGRATION_PORT=20830
GATEWAY_PORT=20030
TRACE_ID=55555555555555555555555555555555
PARENT_SPAN_ID=6666666666666666
CORRELATION_ID=p01-s08-correlation-001
MDM_PID=""
INTEGRATION_PID=""
GATEWAY_PID=""

MDM_LOG="$PWD/p01-s08-mdm.log"
INTEGRATION_LOG="$PWD/p01-s08-integration.log"
GATEWAY_LOG="$PWD/p01-s08-gateway.log"

BOOTSTRAP_EXCLUSIONS="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  set +e
  [[ -n "$GATEWAY_PID" ]] && kill "$GATEWAY_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && kill "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  [[ -n "$GATEWAY_PID" ]] && wait "$GATEWAY_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && wait "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && wait "$MDM_PID" 2>/dev/null

  for container in \
    "$NACOS_CONTAINER" "$REDIS_CONTAINER" "$TEMPO_CONTAINER" "$LOKI_CONTAINER" \
    "$PROMETHEUS_CONTAINER" "$ALLOY_CONTAINER" "$GRAFANA_CONTAINER"; do
    docker logs "$container" > "${container}.log" 2>&1 || true
  done

  docker rm -f \
    "$NACOS_CONTAINER" "$REDIS_CONTAINER" "$TEMPO_CONTAINER" "$LOKI_CONTAINER" \
    "$PROMETHEUS_CONTAINER" "$ALLOY_CONTAINER" "$GRAFANA_CONTAINER" \
    >/dev/null 2>&1 || true
  docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

wait_for_http_200() {
  local url="$1"
  local output="$2"
  local auth="${3:-}"
  for attempt in {1..120}; do
    local status
    if [[ -n "$auth" ]]; then
      status=$(curl --silent --output "$output" --write-out '%{http_code}' --user "$auth" "$url" || true)
    else
      status=$(curl --silent --output "$output" --write-out '%{http_code}' "$url" || true)
    fi
    if [[ "$status" == "200" ]]; then
      return 0
    fi
    if [[ "$attempt" == "120" ]]; then
      echo "Endpoint did not become ready: $url HTTP $status"
      cat "$output" || true
      return 1
    fi
    sleep 2
  done
}

docker rm -f \
  "$NACOS_CONTAINER" "$REDIS_CONTAINER" "$TEMPO_CONTAINER" "$LOKI_CONTAINER" \
  "$PROMETHEUS_CONTAINER" "$ALLOY_CONTAINER" "$GRAFANA_CONTAINER" \
  >/dev/null 2>&1 || true
docker network rm "$NETWORK" >/dev/null 2>&1 || true
docker network create "$NETWORK" >/dev/null

touch "$MDM_LOG" "$INTEGRATION_LOG" "$GATEWAY_LOG"
chmod 644 "$MDM_LOG" "$INTEGRATION_LOG" "$GATEWAY_LOG"

docker run --name "$NACOS_CONTAINER" \
  -e MODE=standalone \
  -e NACOS_AUTH_ENABLE=false \
  -e NACOS_AUTH_TOKEN=TU9NLVBsYXRmb3JtLUNJLU5hY29zLVRva2VuLTIwMjYtMDctMTgtU3Ryb25nLUtleQ== \
  -e NACOS_AUTH_IDENTITY_KEY=mom-platform-ci-key \
  -e NACOS_AUTH_IDENTITY_VALUE=mom-platform-ci-value \
  -e JVM_XMS=256m -e JVM_XMX=256m -e JVM_XMN=128m \
  -p 8848:8848 -p 9848:9848 \
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

docker run --name "$LOKI_CONTAINER" \
  --network "$NETWORK" \
  -v "$PWD/.github/observability/loki-config.yml:/etc/loki/local-config.yaml:ro" \
  -p 3100:3100 \
  -d grafana/loki:3.7.2 \
  -config.file=/etc/loki/local-config.yaml

docker run --name "$PROMETHEUS_CONTAINER" \
  --network "$NETWORK" \
  --add-host=host.docker.internal:host-gateway \
  -v "$PWD/.github/observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  -v "$PWD/.github/observability/mom-alert-rules.yml:/etc/prometheus/mom-alert-rules.yml:ro" \
  -p 9090:9090 \
  -d prom/prometheus:v3.12.0 \
  --config.file=/etc/prometheus/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --storage.tsdb.retention.time=1h \
  --web.enable-lifecycle

docker run --name "$ALLOY_CONTAINER" \
  --network "$NETWORK" \
  -v "$PWD/.github/observability/alloy-config.alloy:/etc/alloy/config.alloy:ro" \
  -v "$PWD:/workspace:ro" \
  -p 12345:12345 \
  -d grafana/alloy:v1.16.1 \
  run /etc/alloy/config.alloy \
  --server.http.listen-addr=0.0.0.0:12345 \
  --storage.path=/var/lib/alloy

docker run --name "$GRAFANA_CONTAINER" \
  --network "$NETWORK" \
  -e GF_SECURITY_ADMIN_USER=admin \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  -e GF_AUTH_ANONYMOUS_ENABLED=false \
  -e GF_USERS_ALLOW_SIGN_UP=false \
  -e GF_ANALYTICS_REPORTING_ENABLED=false \
  -e GF_ANALYTICS_CHECK_FOR_UPDATES=false \
  -v "$PWD/.github/observability/grafana/provisioning:/etc/grafana/provisioning:ro" \
  -v "$PWD/.github/observability/grafana/dashboards:/var/lib/grafana/dashboards:ro" \
  -p 3000:3000 \
  -d grafana/grafana:13.1.0

wait_for_http_200 "http://127.0.0.1:8848/nacos/v3/admin/core/state/readiness" p01-s08-nacos-ready.json
for attempt in {1..120}; do
  if docker exec "$REDIS_CONTAINER" redis-cli ping | grep --quiet PONG; then
    break
  fi
  [[ "$attempt" == "120" ]] && echo "Redis did not become ready" && exit 1
  sleep 2
done
wait_for_http_200 "http://127.0.0.1:3200/ready" p01-s08-tempo-ready.txt
wait_for_http_200 "http://127.0.0.1:3100/ready" p01-s08-loki-ready.txt
wait_for_http_200 "http://127.0.0.1:9090/-/ready" p01-s08-prometheus-ready.txt
wait_for_http_200 "http://127.0.0.1:12345/-/ready" p01-s08-alloy-ready.txt
wait_for_http_200 "http://127.0.0.1:3000/api/health" p01-s08-grafana-health.json admin:admin

COMMON_TRACE_ENV=(
  MOM_ENVIRONMENT=ci
  TRACING_SAMPLING_PROBABILITY=1.0
  OTEL_TRACING_EXPORT_ENABLED=true
  OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://127.0.0.1:4318/v1/traces
  OTEL_EXPORTER_OTLP_TRACES_TIMEOUT=2000
)

# P01-S07 Collector is reused for trace export and remains a separate, explicit telemetry hop.
docker run --name mom-p01-s08-otel-collector \
  --network "$NETWORK" \
  -v "$PWD/.github/observability/otel-collector-config.yml:/etc/otelcol-contrib/config.yaml:ro" \
  -p 4317:4317 -p 4318:4318 -p 13133:13133 \
  -d otel/opentelemetry-collector-contrib:0.156.0 \
  --config=/etc/otelcol-contrib/config.yaml
# Include dynamically named collector in cleanup through explicit trap extension.
COLLECTOR_CONTAINER=mom-p01-s08-otel-collector
wait_for_http_200 "http://127.0.0.1:13133/" p01-s08-collector-ready.txt

MDM_JAR=mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar
INTEGRATION_JAR=mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar
GATEWAY_JAR=mom-gateway/target/mom-gateway-0.1.0-SNAPSHOT-exec.jar

env "${COMMON_TRACE_ENV[@]}" \
  java -jar "$MDM_JAR" \
  --server.port=$MDM_PORT \
  --spring.application.name=mom-mdm-server \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  --seata.enabled=false \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > "$MDM_LOG" 2>&1 &
MDM_PID=$!

env "${COMMON_TRACE_ENV[@]}" REDIS_HOST=127.0.0.1 REDIS_PORT=6379 \
  java -jar "$INTEGRATION_JAR" \
  --server.port=$INTEGRATION_PORT \
  --spring.application.name=mom-integration-server \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  --seata.enabled=false \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > "$INTEGRATION_LOG" 2>&1 &
INTEGRATION_PID=$!

env "${COMMON_TRACE_ENV[@]}" REDIS_HOST=127.0.0.1 REDIS_PORT=6379 \
  GATEWAY_RATE_LIMIT_REPLENISH_RATE=100 \
  GATEWAY_RATE_LIMIT_BURST_CAPACITY=100 \
  GATEWAY_RATE_LIMIT_REQUESTED_TOKENS=1 \
  java -jar "$GATEWAY_JAR" \
  --server.port=$GATEWAY_PORT \
  --spring.application.name=mom-gateway \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  > "$GATEWAY_LOG" 2>&1 &
GATEWAY_PID=$!

for attempt in {1..120}; do
  status=$(curl --silent --output p01-s08-http-response.json \
    --write-out '%{http_code}' \
    --header "X-Correlation-Id: $CORRELATION_ID" \
    --header "traceparent: 00-$TRACE_ID-$PARENT_SPAN_ID-01" \
    "http://127.0.0.1:$GATEWAY_PORT/api/integration/mdm-probe" || true)
  if [[ "$status" == "200" ]]; then
    break
  fi
  [[ "$attempt" == "120" ]] && echo "Gateway request failed with HTTP $status" && cat p01-s08-http-response.json && exit 1
  sleep 2
done

jq --exit-status --arg traceId "$TRACE_ID" '
  .traceId == $traceId and .mdmTraceId == $traceId
' p01-s08-http-response.json >/dev/null

# Prometheus must scrape all three packaged applications and expose real JVM/HTTP metrics.
for attempt in {1..60}; do
  curl --silent --get --data-urlencode 'query=sum(up{job=~"mom-gateway|mom-integration-server|mom-mdm-server"})' \
    http://127.0.0.1:9090/api/v1/query > p01-s08-prometheus-up.json
  up_count=$(jq -r '.data.result[0].value[1] // "0"' p01-s08-prometheus-up.json)
  if [[ "$up_count" == "3" ]]; then
    break
  fi
  [[ "$attempt" == "60" ]] && echo "Prometheus did not scrape all MOM services" && cat p01-s08-prometheus-up.json && exit 1
  sleep 2
done
curl --silent --get --data-urlencode 'query=count(jvm_info{job=~"mom-gateway|mom-integration-server|mom-mdm-server"})' \
  http://127.0.0.1:9090/api/v1/query > p01-s08-prometheus-jvm.json
jq --exit-status '.status == "success" and ((.data.result[0].value[1] | tonumber) >= 3)' p01-s08-prometheus-jvm.json >/dev/null

# Tempo must contain the same fixed trace across Gateway, Integration and MDM.
for attempt in {1..60}; do
  tempo_status=$(curl --silent --output p01-s08-tempo-trace.json --write-out '%{http_code}' \
    "http://127.0.0.1:3200/api/traces/$TRACE_ID" || true)
  if [[ "$tempo_status" == "200" ]] && jq --exit-status '
    [.batches[].resource.attributes[]? | select(.key == "service.name") | .value.stringValue] as $services
    | ($services | index("mom-gateway")) != null
      and ($services | index("mom-integration-server")) != null
      and ($services | index("mom-mdm-server")) != null
  ' p01-s08-tempo-trace.json >/dev/null; then
    break
  fi
  [[ "$attempt" == "60" ]] && echo "Tempo does not contain the complete trace" && cat p01-s08-tempo-trace.json && exit 1
  sleep 1
done

# Alloy must send trace-correlated application logs into Loki without indexing trace_id as a label.
for attempt in {1..60}; do
  curl --silent --get \
    --data-urlencode 'query={environment="ci",service=~"mom-integration-server|mom-mdm-server"} |= "trace_id='$TRACE_ID'"' \
    --data-urlencode 'limit=100' \
    "http://127.0.0.1:3100/loki/api/v1/query_range" > p01-s08-loki-trace-logs.json
  log_streams=$(jq '.data.result | length' p01-s08-loki-trace-logs.json 2>/dev/null || echo 0)
  if [[ "$log_streams" -ge 2 ]]; then
    break
  fi
  [[ "$attempt" == "60" ]] && echo "Loki does not contain trace-correlated logs from both services" && cat p01-s08-loki-trace-logs.json && exit 1
  sleep 1
done
jq --exit-status '
  .status == "success"
  and ([.data.result[].stream.service] | index("mom-integration-server") != null)
  and ([.data.result[].stream.service] | index("mom-mdm-server") != null)
  and ([.data.result[].stream | has("trace_id")] | any | not)
' p01-s08-loki-trace-logs.json >/dev/null

# Grafana provisioning must make all sources healthy and expose the immutable overview dashboard.
for uid in prometheus loki tempo; do
  curl --silent --user admin:admin \
    "http://127.0.0.1:3000/api/datasources/uid/$uid/health" > "p01-s08-grafana-${uid}-health.json"
  jq --exit-status '.status == "OK" or .status == "success"' "p01-s08-grafana-${uid}-health.json" >/dev/null
done
curl --silent --user admin:admin \
  http://127.0.0.1:3000/api/dashboards/uid/mom-platform-overview > p01-s08-grafana-dashboard.json
jq --exit-status '.dashboard.uid == "mom-platform-overview" and (.dashboard.panels | length) >= 6' \
  p01-s08-grafana-dashboard.json >/dev/null

# Query all three backends through Grafana's datasource proxy; direct backend success alone is insufficient.
curl --silent --user admin:admin --get \
  --data-urlencode 'query=sum(up{job=~"mom-gateway|mom-integration-server|mom-mdm-server"})' \
  http://127.0.0.1:3000/api/datasources/proxy/uid/prometheus/api/v1/query \
  > p01-s08-grafana-prometheus-query.json
jq --exit-status '.status == "success" and (.data.result[0].value[1] == "3")' \
  p01-s08-grafana-prometheus-query.json >/dev/null

curl --silent --user admin:admin --get \
  --data-urlencode 'query={environment="ci"} |= "trace_id='$TRACE_ID'"' \
  --data-urlencode 'limit=100' \
  http://127.0.0.1:3000/api/datasources/proxy/uid/loki/loki/api/v1/query_range \
  > p01-s08-grafana-loki-query.json
jq --exit-status '.status == "success" and (.data.result | length) >= 2' \
  p01-s08-grafana-loki-query.json >/dev/null

curl --silent --user admin:admin \
  "http://127.0.0.1:3000/api/datasources/proxy/uid/tempo/api/traces/$TRACE_ID" \
  > p01-s08-grafana-tempo-query.json
jq --exit-status '(.batches | length) >= 3' p01-s08-grafana-tempo-query.json >/dev/null

# Alert lifecycle is verified against a real target outage, not only rule syntax.
kill "$MDM_PID"
wait "$MDM_PID" 2>/dev/null || true
MDM_PID=""
for attempt in {1..60}; do
  curl --silent --get \
    --data-urlencode 'query=ALERTS{alertname="MOMServiceDown",alertstate="firing",job="mom-mdm-server"}' \
    http://127.0.0.1:9090/api/v1/query > p01-s08-prometheus-alert.json
  firing_count=$(jq '.data.result | length' p01-s08-prometheus-alert.json 2>/dev/null || echo 0)
  if [[ "$firing_count" -ge 1 ]]; then
    break
  fi
  [[ "$attempt" == "60" ]] && echo "MOMServiceDown did not fire after a real MDM outage" && cat p01-s08-prometheus-alert.json && exit 1
  sleep 1
done

printf 'metrics=success\nlogs=success\ntraces=success\ngrafana=success\nalert=success\n' \
  > p01-s08-observability-status.txt
