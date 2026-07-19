#!/usr/bin/env bash

prometheus_query() {
  local query=$1
  local output=$2
  curl --fail --silent --get \
    --data-urlencode "query=$query" \
    "http://127.0.0.1:9090/api/v1/query" > "$output"
}

wait_for_prometheus_value() {
  local query=$1
  local description=$2
  for attempt in {1..90}; do
    prometheus_query "$query" p01-s08-prometheus-query.json || true
    value=$(jq -r '.data.result[0].value[1] // "0"' p01-s08-prometheus-query.json 2>/dev/null || echo 0)
    if awk -v value="$value" 'BEGIN { exit !(value > 0) }'; then
      return 0
    fi
    if [[ "$attempt" == 90 ]]; then
      echo "Prometheus query did not become positive: $description, value=$value"
      cat p01-s08-prometheus-query.json || true
      return 1
    fi
    sleep 2
  done
}

wait_for_prometheus_value 'sum(up{job=~"mom-.*"} == 1)' 'all MOM scrape targets'
wait_for_prometheus_value 'sum(http_server_requests_seconds_count{application="mom-integration-server",environment="ci"})' 'HTTP metrics with common tags'
wait_for_prometheus_value 'sum(jvm_memory_used_bytes{application="mom-mdm-server",environment="ci"})' 'JVM metrics'
wait_for_prometheus_value 'sum(hikaricp_connections_max{application=~"mom-(mdm|integration)-server",environment="ci"})' 'Hikari metrics'
wait_for_prometheus_value 'sum(http_server_requests_seconds_bucket{application="mom-integration-server",environment="ci"})' 'HTTP histogram buckets'
wait_for_prometheus_value 'sum(mom_gateway_rate_limit_requests_total{application="mom-gateway",environment="ci",outcome="allowed"})' 'allowed rate-limit counter'
wait_for_prometheus_value 'sum(mom_gateway_rate_limit_requests_total{application="mom-gateway",environment="ci",outcome="rejected"})' 'rejected rate-limit counter'

curl --fail --silent http://127.0.0.1:9090/api/v1/rules > p01-s08-prometheus-rules.json
jq --exit-status '
  [.data.groups[].rules[].name] as $names
  | ($names | index("MOMServiceDown")) != null
  and ($names | index("MOMHttp5xxRateHigh")) != null
  and ($names | index("MOMHikariPoolSaturation")) != null
  and ($names | index("MOMGatewayRateLimitUnavailable")) != null
  and ($names | index("MOMOutboxDeadEvents")) != null
  and ($names | index("MOMInboxProcessingFailures")) != null
' p01-s08-prometheus-rules.json >/dev/null

for attempt in {1..90}; do
  tempo_status=$(curl --silent --output p01-s08-tempo-trace.json \
    --write-out '%{http_code}' \
    "http://127.0.0.1:3200/api/traces/$TRACE_ID" || true)
  if [[ "$tempo_status" == 200 ]] \
    && grep --quiet 'mom-gateway' p01-s08-tempo-trace.json \
    && grep --quiet 'mom-integration-server' p01-s08-tempo-trace.json \
    && grep --quiet 'mom-mdm-server' p01-s08-tempo-trace.json; then
    break
  fi
  if [[ "$attempt" == 90 ]]; then
    echo "Tempo did not contain complete Gateway -> Integration -> MDM trace"
    cat p01-s08-tempo-trace.json || true
    exit 1
  fi
  sleep 2
done

LOKI_QUERY='{service_name="mom-integration-server",environment="ci"} | json | trace_id="'"$TRACE_ID"'"'
for attempt in {1..90}; do
  start_ns=$(date -d '15 minutes ago' +%s%N)
  end_ns=$(date +%s%N)
  curl --fail --silent --get \
    --data-urlencode "query=$LOKI_QUERY" \
    --data-urlencode "start=$start_ns" \
    --data-urlencode "end=$end_ns" \
    --data-urlencode 'limit=100' \
    http://127.0.0.1:3100/loki/api/v1/query_range > p01-s08-loki-query.json || true
  result_count=$(jq '[.data.result[].values[]] | length' p01-s08-loki-query.json 2>/dev/null || echo 0)
  if [[ "$result_count" -gt 0 ]]; then
    break
  fi
  if [[ "$attempt" == 90 ]]; then
    echo "Loki did not contain structured Integration log for trace $TRACE_ID"
    cat p01-s08-loki-query.json || true
    exit 1
  fi
  sleep 2
done

curl --fail --silent http://127.0.0.1:3100/loki/api/v1/labels > p01-s08-loki-labels.json
jq --exit-status '
  (.data | index("service_name")) != null
  and (.data | index("environment")) != null
  and (.data | index("trace_id")) == null
  and (.data | index("span_id")) == null
  and (.data | index("correlation_id")) == null
  and (.data | index("event_id")) == null
' p01-s08-loki-labels.json >/dev/null

curl --fail --silent -u admin:admin http://127.0.0.1:3000/api/health > p01-s08-grafana-health.json
jq --exit-status '.database == "ok"' p01-s08-grafana-health.json >/dev/null
for uid in mom-prometheus mom-loki mom-tempo; do
  curl --fail --silent -u admin:admin \
    "http://127.0.0.1:3000/api/datasources/uid/$uid" \
    > "p01-s08-grafana-datasource-${uid}.json"
  jq --exit-status --arg uid "$uid" '.uid == $uid and .readOnly == true' \
    "p01-s08-grafana-datasource-${uid}.json" >/dev/null
  curl --fail --silent -u admin:admin \
    "http://127.0.0.1:3000/api/datasources/uid/$uid/health" \
    > "p01-s08-grafana-datasource-${uid}-health.json"
done

curl --fail --silent -u admin:admin \
  http://127.0.0.1:3000/api/dashboards/uid/mom-platform-overview \
  > p01-s08-grafana-dashboard.json
jq --exit-status '
  .dashboard.uid == "mom-platform-overview"
  and .dashboard.title == "MOM Platform Overview"
  and (.dashboard.panels | length) >= 4
  and ([.dashboard.panels[].datasource.uid] | index("mom-prometheus")) != null
  and ([.dashboard.panels[].datasource.uid] | index("mom-loki")) != null
' p01-s08-grafana-dashboard.json >/dev/null

docker stop "$PROMETHEUS_CONTAINER" "$LOKI_CONTAINER" >/dev/null
sleep 4
outage_status=$(curl --silent --output p01-s08-backend-outage-response.json \
  --write-out '%{http_code}' \
  --header 'X-Correlation-Id: p01-s08-backend-outage' \
  --header "traceparent: 00-$OUTAGE_TRACE_ID-$OUTAGE_PARENT_SPAN_ID-01" \
  "http://127.0.0.1:${GATEWAY_PORT}/api/integration/mdm-probe" || true)
[[ "$outage_status" == 200 ]]
jq --exit-status --arg traceId "$OUTAGE_TRACE_ID" '
  .traceId == $traceId and .mdmTraceId == $traceId
' p01-s08-backend-outage-response.json >/dev/null

printf 'success=%s\nrejected=%s\nbackendOutage=%s\n' \
  "$success_status" "$rejected_status" "$outage_status" \
  > p01-s08-status.txt
