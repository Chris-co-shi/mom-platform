#!/usr/bin/env bash
set -Eeuo pipefail

NACOS_CONTAINER="mom-nacos-discovery-${GITHUB_RUN_ID:-local}-$$"
NACOS_IMAGE="nacos/nacos-server:v3.1.0"
BOOTSTRAP_EXCLUSIONS="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
MDM_PID=""
INTEGRATION_PID=""

cleanup() {
  set +e
  [[ -n "$INTEGRATION_PID" ]] && kill "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  docker inspect "$NACOS_CONTAINER" > nacos-container-state.json 2>/dev/null || true
  docker logs "$NACOS_CONTAINER" > nacos-discovery-server.log 2>&1 || true
  docker rm -f "$NACOS_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

nacos_test_token="$(printf '%s' 'mom-s04-nacos-discovery-test-token-32-bytes' | base64 | tr -d '\n')"
docker run --name "$NACOS_CONTAINER" \
  -e MODE=standalone -e NACOS_AUTH_ENABLE=false \
  -e NACOS_AUTH_TOKEN="$nacos_test_token" \
  -e NACOS_AUTH_IDENTITY_KEY=mom-s04-test-key \
  -e NACOS_AUTH_IDENTITY_VALUE=mom-s04-test-value \
  -e JVM_XMS=256m -e JVM_XMX=256m -e JVM_XMN=128m \
  -p 8848:8848 -p 9848:9848 -d "$NACOS_IMAGE" >/dev/null

for attempt in {1..45}; do
  curl --fail --silent http://127.0.0.1:8848/nacos/v3/admin/core/state/readiness >/dev/null && break
  [[ "$attempt" == "45" ]] && echo "Nacos did not become ready" >&2 && exit 1
  sleep 2
done

java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port=20200 --spring.application.name=mom-mdm-server \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > mdm-nacos-discovery.log 2>&1 &
MDM_PID=$!

java -jar mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port=20800 --spring.application.name=mom-integration-server \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  --management.health.redis.enabled=false \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > integration-nacos-discovery.log 2>&1 &
INTEGRATION_PID=$!

assert_registered() {
  local service="$1"
  local port="$2"
  local output="$3"
  for attempt in {1..60}; do
    curl --silent --get \
      --data-urlencode "namespaceId=public" \
      --data-urlencode "groupName=DEFAULT_GROUP" \
      --data-urlencode "serviceName=${service}" \
      --data-urlencode "healthyOnly=true" \
      http://127.0.0.1:8848/nacos/v3/client/ns/instance/list > "$output"
    if jq --exit-status --argjson port "$port" \
      '.code == 0 and any(.data[]?; .healthy == true and .enabled == true and .port == $port)' \
      "$output" >/dev/null; then return 0; fi
    [[ "$attempt" == "60" ]] && echo "${service} was not registered as healthy" >&2 && return 1
    sleep 2
  done
}

assert_registered mom-mdm-server 20200 nacos-mdm-instances.json
assert_registered mom-integration-server 20800 nacos-integration-instances.json

kill "$MDM_PID"
wait "$MDM_PID" 2>/dev/null || true
MDM_PID=""
for attempt in {1..20}; do
  curl --silent --get --data-urlencode 'namespaceId=public' --data-urlencode 'groupName=DEFAULT_GROUP' \
    --data-urlencode 'serviceName=mom-mdm-server' --data-urlencode 'healthyOnly=true' \
    http://127.0.0.1:8848/nacos/v3/client/ns/instance/list > nacos-mdm-after-stop.json
  if jq --exit-status '.code == 0 and (.data | length) == 0' nacos-mdm-after-stop.json >/dev/null; then break; fi
  [[ "$attempt" == "20" ]] && echo "MDM instance remained healthy after graceful stop" >&2 && exit 1
  sleep 2
done

echo "NACOS_DISCOVERY_SMOKE result=success mdm=registered integration=registered retirement=no-business-probe"
