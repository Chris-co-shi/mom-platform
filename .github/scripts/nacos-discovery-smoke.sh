#!/usr/bin/env bash
set -Eeuo pipefail

NACOS_CONTAINER="mom-nacos-discovery-${GITHUB_RUN_ID:-local}-$$"
NACOS_IMAGE="nacos/nacos-server:v3.1.0"
CORRELATION_ID="s04-nacos-discovery-001"
BOOTSTRAP_EXCLUSIONS="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
MDM_PID=""
INTEGRATION_PID=""
GATEWAY_PID=""

cleanup() {
  set +e
  [[ -n "$GATEWAY_PID" ]] && kill "$GATEWAY_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && kill "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  docker inspect "$NACOS_CONTAINER" > nacos-container-state.json 2>/dev/null
  docker logs "$NACOS_CONTAINER" > nacos-discovery-server.log 2>&1
  docker rm -f "$NACOS_CONTAINER" >/dev/null 2>&1
}
trap cleanup EXIT

# Nacos 3.1 官方镜像入口脚本要求 Token 与节点身份存在；这些值仅用于隔离 Smoke，
# 不开启客户端鉴权，也不作为 MOM 运行时 Secret 来源。
nacos_test_token="$(printf '%s' 'mom-s04-nacos-discovery-test-token-32-bytes' | base64 | tr -d '\n')"
docker run --name "$NACOS_CONTAINER" \
  -e MODE=standalone \
  -e NACOS_AUTH_ENABLE=false \
  -e NACOS_AUTH_TOKEN="$nacos_test_token" \
  -e NACOS_AUTH_IDENTITY_KEY=mom-s04-test-key \
  -e NACOS_AUTH_IDENTITY_VALUE=mom-s04-test-value \
  -e JVM_XMS=256m \
  -e JVM_XMX=256m \
  -e JVM_XMN=128m \
  -p 8848:8848 \
  -p 9848:9848 \
  -d "$NACOS_IMAGE" >/dev/null

for attempt in {1..45}; do
  container_state="$(docker inspect -f '{{.State.Status}}:{{.State.ExitCode}}:{{.State.OOMKilled}}' "$NACOS_CONTAINER")"
  if [[ "$container_state" != running:* ]]; then
    echo "Nacos container exited before readiness: ${container_state}" >&2
    exit 1
  fi
  if curl --fail --silent --show-error \
    http://127.0.0.1:8848/nacos/v3/admin/core/state/readiness >/dev/null; then
    break
  fi
  if [[ "$attempt" == "45" ]]; then
    echo "Nacos readiness did not become healthy within 90 seconds" >&2
    exit 1
  fi
  sleep 2
done

java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --mom.technical-probe.enabled=true \
  --server.port=20200 \
  --spring.application.name=mom-mdm-server \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > mdm-nacos-discovery.log 2>&1 &
MDM_PID=$!

java -jar mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar \
  --mom.technical-probe.enabled=true \
  --server.port=20800 \
  --spring.application.name=mom-integration-server \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > integration-nacos-discovery.log 2>&1 &
INTEGRATION_PID=$!

# Smoke 专用配置只覆盖路由列表，移除 Redis RateLimiter，避免 Nacos 结论依赖 Redis。
SPRING_APPLICATION_JSON='{"spring":{"cloud":{"gateway":{"server":{"webflux":{"routes":[{"id":"integration-discovery-smoke","uri":"lb://mom-integration-server","predicates":["Path=/api/integration/**"],"filters":["StripPrefix=1"]}]}}}}}}' \
GATEWAY_SECURITY_ENABLED=false \
MOM_TECHNICAL_PROBE_ENABLED=true \
java -jar mom-gateway/target/mom-gateway-0.1.0-SNAPSHOT-exec.jar \
  --server.port=20000 \
  --spring.application.name=mom-gateway \
  --spring.cloud.nacos.discovery.enabled=true \
  --spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 \
  --spring.cloud.nacos.discovery.ip=127.0.0.1 \
  > gateway-nacos-discovery.log 2>&1 &
GATEWAY_PID=$!

for attempt in {1..60}; do
  response_status="$(curl --silent --output nacos-discovery-response.json \
    --dump-header nacos-discovery-headers.txt \
    --write-out '%{http_code}' \
    --header "X-Correlation-Id: ${CORRELATION_ID}" \
    http://127.0.0.1:20000/api/integration/mdm-probe || true)"
  if [[ "$response_status" == "200" ]]; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    echo "Gateway discovery request failed with HTTP ${response_status}" >&2
    exit 1
  fi
  sleep 2
done

grep --ignore-case --quiet "^X-Correlation-Id: ${CORRELATION_ID}" nacos-discovery-headers.txt
jq --exit-status --arg correlationId "$CORRELATION_ID" '
  .service == "mom-integration-server"
  and .status == "UP"
  and .correlationId == $correlationId
  and .mdmService == "mom-mdm-server"
  and .mdmStatus == "UP"
  and .mdmCorrelationId == $correlationId
' nacos-discovery-response.json >/dev/null

# Nacos 中断后，Gateway/Client 可能短期使用已缓存实例；这是当前真实行为，不把它误报为发现服务仍健康。
docker stop "$NACOS_CONTAINER" >/dev/null
sleep 3
cached_outage_status="$(curl --silent --output nacos-discovery-outage.json \
  --write-out '%{http_code}' \
  http://127.0.0.1:20000/api/integration/mdm-probe || true)"

# 在 Nacos 已不可用时再移除缓存指向的 Integration 实例，必须得到真实路由失败而非伪成功。
kill "$INTEGRATION_PID"
wait "$INTEGRATION_PID" 2>/dev/null || true
INTEGRATION_PID=""
for attempt in {1..10}; do
  missing_instance_status="$(curl --silent --output nacos-discovery-missing-instance.json \
    --write-out '%{http_code}' \
    http://127.0.0.1:20000/api/integration/mdm-probe || true)"
  if [[ "$missing_instance_status" != "200" ]]; then break; fi
  if [[ "$attempt" == "10" ]]; then
    echo "Gateway returned success after Nacos and the cached Integration instance were unavailable" >&2
    exit 1
  fi
  sleep 1
done

echo "NACOS_DISCOVERY_SMOKE result=success image=${NACOS_IMAGE} readiness=success route=success cached_outage_http=${cached_outage_status} missing_instance_http=${missing_instance_status}"
