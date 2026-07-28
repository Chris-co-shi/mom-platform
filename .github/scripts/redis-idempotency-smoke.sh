#!/usr/bin/env bash
set -Eeuo pipefail

REDIS_CONTAINER="mom-redis-idempotency-${GITHUB_RUN_ID:-local}-$$"
REDIS_IMAGE="redis:8.4.4-alpine"
INTEGRATION_PID=""
BOOTSTRAP_EXCLUSIONS="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  set +e
  [[ -n "$INTEGRATION_PID" ]] && kill "$INTEGRATION_PID" 2>/dev/null
  docker inspect "$REDIS_CONTAINER" > redis-idempotency-container-state.json 2>/dev/null
  docker logs "$REDIS_CONTAINER" > redis-idempotency-server.log 2>&1
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

REDIS_HOST=127.0.0.1 REDIS_PORT=6379 IDEMPOTENCY_DEFAULT_TTL=30s \
java -jar mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar \
  --mom.technical-probe.enabled=true \
  --server.port=20800 \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > integration-redis-idempotency.log 2>&1 &
INTEGRATION_PID=$!

for attempt in {1..45}; do
  health_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
    http://127.0.0.1:20800/actuator/health/readiness || true)"
  [[ "$health_status" == "200" ]] && break
  if [[ "$attempt" == "45" ]]; then echo "Integration did not become ready" >&2; exit 1; fi
  sleep 2
done

request_probe() {
  local key="$1"
  local output="$2"
  curl --silent --output "$output" --write-out '%{http_code}' --request POST \
    --header "X-Correlation-Id: s04-redis-idempotency" \
    --header "Idempotency-Key: ${key}" \
    http://127.0.0.1:20800/integration/idempotency-probe
}

first_status="$(request_probe 'Case-Key' idempotency-first.json)"
duplicate_status="$(request_probe 'Case-Key' idempotency-duplicate.json)"
lower_status="$(request_probe 'case-key' idempotency-case.json)"
unicode_status="$(request_probe '键-é' idempotency-unicode.json)"
space_status="$(request_probe 'space key' idempotency-space.json)"
compact_status="$(request_probe 'spacekey' idempotency-compact.json)"
[[ "$first_status" == "201" && "$duplicate_status" == "409" ]]
[[ "$lower_status" == "201" && "$unicode_status" == "201" ]]
[[ "$space_status" == "201" && "$compact_status" == "201" ]]
jq --exit-status '.status == "ACQUIRED" and .mayProceed == true' idempotency-first.json >/dev/null
jq --exit-status '.status == "DUPLICATE" and .mayProceed == false' idempotency-duplicate.json >/dev/null

redis_key_count=0
while IFS= read -r redis_key; do
  [[ -z "$redis_key" ]] && continue
  redis_key_count=$((redis_key_count + 1))
  [[ "$redis_key" != *Case-Key* && "$redis_key" != *case-key* && "$redis_key" != *'键-é'* ]]
  key_ttl="$(docker exec "$REDIS_CONTAINER" redis-cli TTL "$redis_key")"
  (( key_ttl > 0 && key_ttl <= 30 ))
done < <(docker exec "$REDIS_CONTAINER" redis-cli --raw --scan --pattern 'mom:*:idempotency:*')
[[ "$redis_key_count" -eq 5 ]]

docker stop "$REDIS_CONTAINER" >/dev/null
failure_status="$(request_probe 'outage-key' redis-idempotency-failure.json || true)"
[[ "$failure_status" == "503" ]]
jq --exit-status '.status == "UNAVAILABLE" and .mayProceed == false' redis-idempotency-failure.json >/dev/null

echo "REDIS_IDEMPOTENCY_SMOKE result=success first=201 duplicate=409 variants=distinct ttl=bounded outage=503"
