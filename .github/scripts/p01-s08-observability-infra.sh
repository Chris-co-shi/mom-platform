#!/usr/bin/env bash

# 该文件由 p01-s08-observability-stack-smoke.sh source，共享其容器名、网络和日志目录变量。
docker run --name "$POSTGRES_CONTAINER" \
  --network "$NETWORK" \
  -e POSTGRES_DB=mom_platform \
  -e POSTGRES_USER=mom \
  -e POSTGRES_PASSWORD=mom \
  -p ${POSTGRES_PORT}:5432 \
  -d postgres:17.7-alpine \
  postgres -c fsync=off -c timezone=Asia/Tokyo

docker run --name "$REDIS_CONTAINER" \
  --network "$NETWORK" \
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
  -v "$PWD/.github/observability/otel-collector-stack-config.yml:/etc/otelcol-contrib/config.yaml:ro" \
  -p 4318:4318 \
  -p 13133:13133 \
  -d otel/opentelemetry-collector-contrib:0.156.0 \
  --config=/etc/otelcol-contrib/config.yaml

docker run --name "$LOKI_CONTAINER" \
  --network "$NETWORK" \
  -v "$PWD/.github/observability/loki-config.yml:/etc/loki/config.yml:ro" \
  -p 3100:3100 \
  -d grafana/loki:3.7.2 \
  -config.file=/etc/loki/config.yml

docker run --name "$PROMETHEUS_CONTAINER" \
  --network "$NETWORK" \
  --add-host=host.docker.internal:host-gateway \
  -v "$PWD/.github/observability/prometheus-config.yml:/etc/prometheus/prometheus.yml:ro" \
  -v "$PWD/.github/observability/mom-alert-rules.yml:/etc/prometheus/mom-alert-rules.yml:ro" \
  -p 9090:9090 \
  -d prom/prometheus:v3.12.0 \
  --config.file=/etc/prometheus/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --web.enable-lifecycle

docker run --name "$ALLOY_CONTAINER" \
  --network "$NETWORK" \
  -v "$PWD/.github/observability/alloy-config.alloy:/etc/alloy/config.alloy:ro" \
  -v "$LOG_DIR:/var/log/mom:ro" \
  -p 12345:12345 \
  -d grafana/alloy:v1.16.1 \
  run /etc/alloy/config.alloy --server.http.listen-addr=0.0.0.0:12345

docker run --name "$GRAFANA_CONTAINER" \
  --network "$NETWORK" \
  -e GF_SECURITY_ADMIN_USER=admin \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  -e GF_USERS_ALLOW_SIGN_UP=false \
  -v "$PWD/.github/observability/grafana/provisioning:/etc/grafana/provisioning:ro" \
  -v "$PWD/.github/observability/grafana/dashboards:/var/lib/grafana/dashboards:ro" \
  -p 3000:3000 \
  -d grafana/grafana:13.1.0

for attempt in {1..120}; do
  postgres_ready=false
  redis_ready=false
  tempo_ready=false
  collector_ready=false
  prometheus_ready=false
  loki_ready=false
  alloy_ready=false
  grafana_ready=false

  docker exec "$POSTGRES_CONTAINER" pg_isready -U mom -d mom_platform >/dev/null 2>&1 && postgres_ready=true
  docker exec "$REDIS_CONTAINER" redis-cli ping 2>/dev/null | grep --quiet PONG && redis_ready=true
  curl --fail --silent http://127.0.0.1:3200/ready >/dev/null && tempo_ready=true
  curl --fail --silent http://127.0.0.1:13133/ >/dev/null && collector_ready=true
  curl --fail --silent http://127.0.0.1:9090/-/ready >/dev/null && prometheus_ready=true
  curl --fail --silent http://127.0.0.1:3100/ready >/dev/null && loki_ready=true
  curl --fail --silent http://127.0.0.1:12345/-/ready >/dev/null && alloy_ready=true
  curl --fail --silent http://127.0.0.1:3000/api/health >/dev/null && grafana_ready=true

  if [[ "$postgres_ready" == true && "$redis_ready" == true && "$tempo_ready" == true \
    && "$collector_ready" == true && "$prometheus_ready" == true && "$loki_ready" == true \
    && "$alloy_ready" == true && "$grafana_ready" == true ]]; then
    break
  fi
  if [[ "$attempt" == 120 ]]; then
    echo "Observability stack did not become ready"
    exit 1
  fi
  sleep 2
done
