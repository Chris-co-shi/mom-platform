#!/usr/bin/env bash
set -Eeuo pipefail

REDIS_CONTAINER="mom-redis-rate-limit-${GITHUB_RUN_ID:-local}-$$"
REDIS_IMAGE="redis:8.4.4-alpine"
DOWNSTREAM_PID=""
GATEWAY_ONE_PID=""
GATEWAY_TWO_PID=""

cleanup() {
  set +e
  [[ -n "$GATEWAY_TWO_PID" ]] && kill "$GATEWAY_TWO_PID" 2>/dev/null
  [[ -n "$GATEWAY_ONE_PID" ]] && kill "$GATEWAY_ONE_PID" 2>/dev/null
  [[ -n "$DOWNSTREAM_PID" ]] && kill "$DOWNSTREAM_PID" 2>/dev/null
  [[ -n "$GATEWAY_TWO_PID" ]] && wait "$GATEWAY_TWO_PID" 2>/dev/null
  [[ -n "$GATEWAY_ONE_PID" ]] && wait "$GATEWAY_ONE_PID" 2>/dev/null
  [[ -n "$DOWNSTREAM_PID" ]] && wait "$DOWNSTREAM_PID" 2>/dev/null
  docker inspect "$REDIS_CONTAINER" > redis-rate-limit-container-state.json 2>/dev/null
  docker logs "$REDIS_CONTAINER" > redis-rate-limit-server.log 2>&1
  docker rm -f "$REDIS_CONTAINER" >/dev/null 2>&1
}
trap cleanup EXIT

docker run --name "$REDIS_CONTAINER" -p 6379:6379 -d "$REDIS_IMAGE" \
  redis-server --save "" --appendonly no >/dev/null
for attempt in {1..30}; do
  if docker exec "$REDIS_CONTAINER" redis-cli ping | grep --quiet PONG; then break; fi
  if [[ "$attempt" == "30" ]]; then echo "Redis did not become ready" >&2; exit 1; fi
  sleep 1
done

# Smoke 专用静态下游不进入生产配置，只证明两个打包 Gateway 共享同一 Redis 配额。
python3 -c 'from http.server import BaseHTTPRequestHandler,HTTPServer
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body=b"{\"status\":\"UP\"}"
        self.send_response(200); self.send_header("Content-Type","application/json")
        self.send_header("Content-Length",str(len(body))); self.end_headers(); self.wfile.write(body)
    def log_message(self, format, *args): pass
HTTPServer(("127.0.0.1",20900),Handler).serve_forever()' > redis-rate-limit-downstream.log 2>&1 &
DOWNSTREAM_PID=$!

gateway_routes='{"spring":{"cloud":{"gateway":{"server":{"webflux":{"routes":[{"id":"redis-rate-limit-smoke","uri":"http://127.0.0.1:20900","predicates":["Path=/api/integration/**"],"filters":[{"name":"RequestRateLimiter","args":{"rate-limiter":"#{@momFailClosedRedisRateLimiter}","key-resolver":"#{@requestIdentityKeyResolver}","redis-rate-limiter.replenishRate":"1","redis-rate-limiter.burstCapacity":"3","redis-rate-limiter.requestedTokens":"3"}}]}]}}}}}}'

start_gateway() {
  local port="$1"
  local log_file="$2"
  SPRING_APPLICATION_JSON="$gateway_routes" \
  REDIS_HOST=127.0.0.1 REDIS_PORT=6379 \
  GATEWAY_SECURITY_ENABLED=false MOM_TECHNICAL_PROBE_ENABLED=true \
  java -jar mom-gateway/target/mom-gateway-0.1.0-SNAPSHOT-exec.jar \
    --server.port="$port" \
    --spring.cloud.nacos.discovery.enabled=false \
    > "$log_file" 2>&1 &
  printf '%s' "$!"
}

GATEWAY_ONE_PID="$(start_gateway 20000 gateway-one-redis-rate-limit.log)"
GATEWAY_TWO_PID="$(start_gateway 20001 gateway-two-redis-rate-limit.log)"

for attempt in {1..45}; do
  first_status="$(curl --silent --output rate-limit-first.json --write-out '%{http_code}' \
    http://127.0.0.1:20000/api/integration/mdm-probe || true)"
  [[ "$first_status" == "200" ]] && break
  if [[ "$attempt" == "45" ]]; then echo "First Gateway did not become ready" >&2; exit 1; fi
  sleep 2
done
second_status="$(curl --silent --output rate-limit-second.json --write-out '%{http_code}' \
  http://127.0.0.1:20001/api/integration/mdm-probe || true)"
[[ "$second_status" == "429" ]]

docker stop "$REDIS_CONTAINER" >/dev/null
failure_status="$(curl --silent --output redis-rate-limit-failure.json --write-out '%{http_code}' \
  http://127.0.0.1:20000/api/integration/mdm-probe || true)"
[[ "$failure_status" == "503" ]]
jq --exit-status '.code == "REDIS_RATE_LIMIT_UNAVAILABLE"' redis-rate-limit-failure.json >/dev/null

docker start "$REDIS_CONTAINER" >/dev/null
for attempt in {1..30}; do
  if docker exec "$REDIS_CONTAINER" redis-cli ping | grep --quiet PONG; then break; fi
  if [[ "$attempt" == "30" ]]; then echo "Redis did not recover" >&2; exit 1; fi
  sleep 1
done
for attempt in {1..20}; do
  recovery_status="$(curl --silent --output rate-limit-recovery.json --write-out '%{http_code}' \
    http://127.0.0.1:20000/api/integration/mdm-probe || true)"
  [[ "$recovery_status" == "200" ]] && break
  if [[ "$attempt" == "20" ]]; then echo "Gateway did not recover after Redis restart" >&2; exit 1; fi
  sleep 1
done

curl --fail --silent http://127.0.0.1:20000/actuator/prometheus > redis-rate-limit-prometheus.txt
grep --extended-regexp --quiet 'mom_gateway_rate_limit_requests_total\{[^}]*outcome="(allowed|rejected|unavailable)"[^}]*route="redis-rate-limit-smoke"' redis-rate-limit-prometheus.txt
if grep --quiet 'requestIdentity' redis-rate-limit-prometheus.txt; then
  echo "Rate-limit metric leaked request identity" >&2
  exit 1
fi

echo "REDIS_RATE_LIMIT_SMOKE result=success shared_quota=429 outage=503 recovery=200 metrics=low-cardinality"
