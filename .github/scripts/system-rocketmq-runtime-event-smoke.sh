#!/usr/bin/env bash
set -Eeuo pipefail

POSTGRES_CONTAINER="mom-system-runtime-postgres-${$}-${RANDOM}"
REDIS_CONTAINER="mom-system-runtime-redis-${$}-${RANDOM}"
NAMESRV_CONTAINER="mom-system-runtime-namesrv-${$}-${RANDOM}"
BROKER_CONTAINER="mom-system-runtime-broker-${$}-${RANDOM}"
POSTGRES_DATABASE="mom_platform"
POSTGRES_USERNAME="mom"
POSTGRES_PASSWORD="mom"
POSTGRES_SCHEMA="mom_system"
POSTGRES_PORT=""
REDIS_PORT=""
SYSTEM_PORT="20303"
SYSTEM_PID=""
JWKS_PORT="19090"
JWKS_PID=""
KEY_DIR="$(mktemp -d)"
ISSUER="http://127.0.0.1:${JWKS_PORT}"
JWT_CLIENT_ID="mom-admin-web"
JWT_KEY_ID="s18-runtime-event-smoke"
ROCKETMQ_IMAGE="apache/rocketmq:5.3.2"
ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
EVENT_TOPIC="mom-system-runtime-events-v1"
CONSUMER_GROUP="mom-system-runtime-cache-invalidation-v1"
ENVIRONMENT="s18-ci"
PARAMETER_KEY="s18.cache.probe"
CACHE_INDEX="mom:${ENVIRONMENT}:system:parameter-resolved-index:v1:${PARAMETER_KEY}"
BROKER_CONFIG="$(pwd)/system-runtime-event-broker.conf"
SECURITY_EXCLUSIONS="org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration,org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration"
FAILURE_REASON="System Runtime Event smoke failed"

cleanup() {
  exit_code=$?
  set +e
  [[ -n "$SYSTEM_PID" ]] && kill "$SYSTEM_PID" 2>/dev/null
  [[ -n "$SYSTEM_PID" ]] && wait "$SYSTEM_PID" 2>/dev/null
  [[ -n "$JWKS_PID" ]] && kill "$JWKS_PID" 2>/dev/null
  [[ -n "$JWKS_PID" ]] && wait "$JWKS_PID" 2>/dev/null
  docker logs "$POSTGRES_CONTAINER" > system-runtime-event-postgresql.log 2>&1
  docker logs "$REDIS_CONTAINER" > system-runtime-event-redis.log 2>&1
  docker logs "$NAMESRV_CONTAINER" > system-runtime-event-namesrv.log 2>&1
  docker logs "$BROKER_CONTAINER" > system-runtime-event-broker.log 2>&1
  docker rm -f "$POSTGRES_CONTAINER" >/dev/null 2>&1
  docker rm -f "$REDIS_CONTAINER" >/dev/null 2>&1
  docker rm -f "$NAMESRV_CONTAINER" >/dev/null 2>&1
  docker rm -f "$BROKER_CONTAINER" >/dev/null 2>&1
  rm -f "$BROKER_CONFIG"
  rm -rf "$KEY_DIR"
  if [[ "$exit_code" -ne 0 ]]; then
    printf 'SYSTEM_RUNTIME_EVENT_SMOKE result=failure reason=%s\n' "$FAILURE_REASON" >&2
  fi
  exit "$exit_code"
}
trap cleanup EXIT

fail() {
  FAILURE_REASON="$1"
  return 1
}

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

wait_http_200() {
  local url="$1"
  local output="$2"
  local name="$3"
  local status=""
  for attempt in {1..90}; do
    status=$(curl --silent --output "$output" --write-out '%{http_code}' "$url" || true)
    if [[ "$status" == "200" ]]; then
      return 0
    fi
    sleep 2
  done
  fail "${name} did not become ready; HTTP ${status}"
}

sql_value() {
  local sql="$1"
  docker exec "$POSTGRES_CONTAINER" psql \
    -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc \
    "SET search_path TO ${POSTGRES_SCHEMA}; ${sql}" 2>/dev/null \
    | tail -n 1 | tr -d '[:space:]'
}

wait_sql_value() {
  local sql="$1"
  local expected="$2"
  local description="$3"
  local actual=""
  for attempt in {1..120}; do
    actual=$(sql_value "$sql")
    if [[ "$actual" == "$expected" ]]; then
      return 0
    fi
    sleep 1
  done
  fail "${description}; expected=${expected}, actual=${actual}"
}

wait_new_event() {
  local aggregate_id="$1"
  local excluded_id="$2"
  local event_id=""
  for attempt in {1..60}; do
    event_id=$(sql_value "SELECT event_id FROM mom_outbox_event WHERE aggregate_id='${aggregate_id}' AND event_id <> '${excluded_id}' ORDER BY created_at DESC LIMIT 1")
    if [[ -n "$event_id" ]]; then
      printf '%s' "$event_id"
      return 0
    fi
    sleep 1
  done
  fail "new Outbox event was not created for aggregate ${aggregate_id}"
}

wait_broker() {
  for attempt in {1..90}; do
    if docker exec "$BROKER_CONTAINER" sh mqadmin clusterList \
        -n "$ROCKETMQ_NAME_SERVER" 2>/dev/null | grep --quiet 'DefaultCluster'; then
      return 0
    fi
    sleep 2
  done
  fail "RocketMQ Broker did not become ready"
}

assert_cache_present() {
  local actual
  actual=$(docker exec "$REDIS_CONTAINER" redis-cli EXISTS "$CACHE_INDEX" | tr -d '[:space:]')
  [[ "$actual" == "1" ]] || fail "Parameter Runtime Cache index was not populated"
}

wait_cache_absent() {
  local actual=""
  for attempt in {1..60}; do
    actual=$(docker exec "$REDIS_CONTAINER" redis-cli EXISTS "$CACHE_INDEX" 2>/dev/null | tr -d '[:space:]')
    if [[ "$actual" == "0" ]]; then
      return 0
    fi
    sleep 1
  done
  fail "Parameter Runtime Cache index was not invalidated"
}

cat > "$BROKER_CONFIG" <<'EOF'
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0
brokerIP1=127.0.0.1
listenPort=10911
deleteWhen=04
fileReservedTime=1
brokerRole=ASYNC_MASTER
flushDiskType=ASYNC_FLUSH
autoCreateTopicEnable=true
autoCreateSubscriptionGroup=true
EOF

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${KEY_DIR}/jwt-private.pem" >/dev/null 2>&1
MODULUS_HEX=$(openssl rsa -in "${KEY_DIR}/jwt-private.pem" -noout -modulus | cut -d= -f2)
MODULUS=$(printf '%s' "$MODULUS_HEX" | xxd -r -p | base64url)
jq --null-input --compact-output \
  --arg kid "$JWT_KEY_ID" --arg n "$MODULUS" \
  '{keys:[{kty:"RSA",use:"sig",alg:"RS256",kid:$kid,n:$n,e:"AQAB"}]}' \
  > "${KEY_DIR}/jwks.json"
python3 -m http.server "$JWKS_PORT" --bind 127.0.0.1 --directory "$KEY_DIR" \
  > system-runtime-event-jwks.log 2>&1 &
JWKS_PID=$!
wait_http_200 "${ISSUER}/jwks.json" system-runtime-event-jwks.json "JWKS server"

NOW=$(date +%s)
EXPIRES=$((NOW + 3600))
JWT_HEADER=$(jq --null-input --compact-output \
  --arg kid "$JWT_KEY_ID" '{alg:"RS256",typ:"JWT",kid:$kid}')
JWT_PAYLOAD=$(jq --null-input --compact-output \
  --arg iss "$ISSUER" --arg sub "s18-smoke-user" --arg jti "s18-smoke-jti" \
  --arg sid "s18-smoke-session" --arg client "$JWT_CLIENT_ID" \
  --argjson iat "$NOW" --argjson exp "$EXPIRES" \
  '{iss:$iss,sub:$sub,jti:$jti,sid:$sid,client_id:$client,user_type:"INTERNAL",aud:[$client],iat:$iat,exp:$exp,roles:["PLATFORM_ADMIN"],permissions:["system:parameter:read","system:parameter:write"],factory_ids:[]}')
JWT_SIGNING_INPUT="$(printf '%s' "$JWT_HEADER" | base64url).$(printf '%s' "$JWT_PAYLOAD" | base64url)"
JWT_SIGNATURE=$(printf '%s' "$JWT_SIGNING_INPUT" \
  | openssl dgst -sha256 -sign "${KEY_DIR}/jwt-private.pem" \
  | base64url)
ACCESS_TOKEN="${JWT_SIGNING_INPUT}.${JWT_SIGNATURE}"
AUTH_HEADER="Authorization: Bearer ${ACCESS_TOKEN}"

docker run --name "$POSTGRES_CONTAINER" \
  -e POSTGRES_DB="$POSTGRES_DATABASE" \
  -e POSTGRES_USER="$POSTGRES_USERNAME" \
  -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  -p 127.0.0.1::5432 -d postgres:17.7-alpine \
  postgres -c timezone=Asia/Tokyo >/dev/null
POSTGRES_PORT=$(docker port "$POSTGRES_CONTAINER" 5432/tcp | awk -F: 'NR == 1 {print $NF}')
[[ -n "$POSTGRES_PORT" ]] || fail "cannot resolve PostgreSQL port"

docker run --name "$REDIS_CONTAINER" -p 127.0.0.1::6379 \
  -d redis:7.4.2-alpine redis-server --save '' --appendonly no >/dev/null
REDIS_PORT=$(docker port "$REDIS_CONTAINER" 6379/tcp | awk -F: 'NR == 1 {print $NF}')
[[ -n "$REDIS_PORT" ]] || fail "cannot resolve Redis port"

docker run --name "$NAMESRV_CONTAINER" --network host \
  -e JAVA_OPT_EXT='-Xms256m -Xmx256m -Xmn128m' \
  -d "$ROCKETMQ_IMAGE" sh mqnamesrv >/dev/null

docker run --name "$BROKER_CONTAINER" --network host \
  -e NAMESRV_ADDR="$ROCKETMQ_NAME_SERVER" \
  -e JAVA_OPT_EXT='-Xms384m -Xmx384m -Xmn128m' \
  -v "$BROKER_CONFIG:/home/rocketmq/broker.conf:ro" \
  -d "$ROCKETMQ_IMAGE" \
  sh mqbroker -n "$ROCKETMQ_NAME_SERVER" -c /home/rocketmq/broker.conf >/dev/null

for attempt in {1..60}; do
  docker exec "$POSTGRES_CONTAINER" pg_isready -U "$POSTGRES_USERNAME" \
    -d "$POSTGRES_DATABASE" >/dev/null 2>&1 && break
  [[ "$attempt" != "60" ]] || fail "PostgreSQL did not become ready"
  sleep 2
done
for attempt in {1..60}; do
  docker exec "$REDIS_CONTAINER" redis-cli ping 2>/dev/null | grep --quiet PONG && break
  [[ "$attempt" != "60" ]] || fail "Redis did not become ready"
  sleep 1
done
wait_broker

docker exec "$BROKER_CONTAINER" sh mqadmin updateTopic \
  -n "$ROCKETMQ_NAME_SERVER" -c DefaultCluster -t "$EVENT_TOPIC" >/dev/null

bash scripts/codex-mvn-test.sh \
  -pl mom-system-platform/mom-system-server -am -DskipTests package

POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" POSTGRES_SCHEMA="$POSTGRES_SCHEMA" \
POSTGRES_USERNAME="$POSTGRES_USERNAME" POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
REDIS_HOST=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
NACOS_DISCOVERY_ENABLED=false MANAGEMENT_HEALTH_REDIS_ENABLED=false \
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=false \
MOM_RESOURCE_SERVER_ENABLED=true IAM_ISSUER_URI="$ISSUER" \
IAM_JWK_SET_URI="${ISSUER}/jwks.json" IAM_ACCEPTED_AUDIENCES="$JWT_CLIENT_ID" \
IAM_PERMISSION_REFERENCE_OAUTH2_ENABLED=false \
SYSTEM_RUNTIME_CACHE_ENABLED=true MOM_ENVIRONMENT="$ENVIRONMENT" \
SYSTEM_RUNTIME_EVENT_CONSUMER_ENABLED=true \
SYSTEM_STREAM_FUNCTION_DEFINITION=systemRuntimeChangeConsumer \
SYSTEM_RUNTIME_EVENT_TOPIC="$EVENT_TOPIC" \
SYSTEM_RUNTIME_EVENT_CONSUMER_GROUP="$CONSUMER_GROUP" \
SYSTEM_RUNTIME_EVENT_PRODUCER_GROUP=mom-system-runtime-events-producer-v1 \
ROCKETMQ_NAME_SERVER="$ROCKETMQ_NAME_SERVER" ROCKETMQ_MAX_RECONSUME_TIMES=2 \
OUTBOX_PUBLISHER_ENABLED=true OUTBOX_FIXED_DELAY_MILLIS=250 \
OUTBOX_INITIAL_BACKOFF=1s OUTBOX_MAX_BACKOFF=5s OUTBOX_MAX_ATTEMPTS=8 \
java -jar mom-system-platform/mom-system-server/target/mom-system-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$SYSTEM_PORT" \
  --mom.system.catalog.permission-reconciliation.enabled=false \
  --management.health.redis.enabled=false \
  > system-runtime-event-server.log 2>&1 &
SYSTEM_PID=$!
wait_http_200 "http://127.0.0.1:${SYSTEM_PORT}/actuator/health/readiness" \
  system-runtime-event-health.json "secured System"

CREATE_STATUS=$(curl --silent --output system-runtime-event-create.json --write-out '%{http_code}' \
  --request POST --header "$AUTH_HEADER" --header 'Content-Type: application/json' \
  --data '{"scopeType":"GLOBAL","scopeCode":null,"parameterKey":"s18.cache.probe","valueType":"STRING","parameterValue":"v1","description":"S18 Runtime Event smoke","enabled":true}' \
  "http://127.0.0.1:${SYSTEM_PORT}/api/system/admin/parameters")
[[ "$CREATE_STATUS" == "201" ]] || fail "Parameter create failed; HTTP ${CREATE_STATUS}"
PARAMETER_ID=$(jq --raw-output '.id // empty' system-runtime-event-create.json)
PARAMETER_VERSION=$(jq --raw-output '.version // empty' system-runtime-event-create.json)
[[ -n "$PARAMETER_ID" && "$PARAMETER_VERSION" == "0" ]] || fail "Parameter create response is invalid"
CREATE_EVENT=$(wait_new_event "$PARAMETER_ID" "-")
wait_sql_value "SELECT status FROM mom_outbox_event WHERE event_id='${CREATE_EVENT}'" "SENT" "create Outbox was not sent"
wait_sql_value "SELECT count(*) FROM mom_inbox_event WHERE event_id='${CREATE_EVENT}' AND processed_at IS NOT NULL" "1" "create Inbox was not processed"

RUNTIME_STATUS=$(curl --silent --output system-runtime-event-runtime-v1.json --write-out '%{http_code}' \
  --header "$AUTH_HEADER" \
  "http://127.0.0.1:${SYSTEM_PORT}/api/system/parameters/${PARAMETER_KEY}")
[[ "$RUNTIME_STATUS" == "200" ]] || fail "Parameter Runtime v1 failed; HTTP ${RUNTIME_STATUS}"
jq --exit-status '.parameterValue == "v1" and .version == 0' \
  system-runtime-event-runtime-v1.json >/dev/null || fail "Parameter Runtime v1 response is invalid"
assert_cache_present

UPDATE_STATUS=$(curl --silent --output system-runtime-event-update-v2.json --write-out '%{http_code}' \
  --request PUT --header "$AUTH_HEADER" --header 'Content-Type: application/json' \
  --data '{"version":0,"valueType":"STRING","parameterValue":"v2","description":"S18 Runtime Event smoke v2"}' \
  "http://127.0.0.1:${SYSTEM_PORT}/api/system/admin/parameters/${PARAMETER_ID}")
[[ "$UPDATE_STATUS" == "200" ]] || fail "Parameter update v2 failed; HTTP ${UPDATE_STATUS}"
UPDATE_EVENT=$(wait_new_event "$PARAMETER_ID" "$CREATE_EVENT")
wait_sql_value "SELECT status FROM mom_outbox_event WHERE event_id='${UPDATE_EVENT}'" "SENT" "update Outbox was not sent"
wait_sql_value "SELECT count(*) FROM mom_inbox_event WHERE event_id='${UPDATE_EVENT}' AND processed_at IS NOT NULL" "1" "update Inbox was not processed"
wait_cache_absent

curl --fail --silent --show-error --header "$AUTH_HEADER" \
  "http://127.0.0.1:${SYSTEM_PORT}/api/system/parameters/${PARAMETER_KEY}" \
  > system-runtime-event-runtime-v2.json
jq --exit-status '.parameterValue == "v2" and .version == 1' \
  system-runtime-event-runtime-v2.json >/dev/null || fail "Parameter Runtime v2 response is invalid"
assert_cache_present

docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USERNAME" \
  -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 -c \
  "SET search_path TO ${POSTGRES_SCHEMA}; UPDATE mom_outbox_event SET status='RETRY', next_attempt_at=CURRENT_TIMESTAMP, sent_at=NULL, lease_owner=NULL, lease_until=NULL, updated_at=CURRENT_TIMESTAMP WHERE event_id='${UPDATE_EVENT}';" >/dev/null
wait_sql_value "SELECT status FROM mom_outbox_event WHERE event_id='${UPDATE_EVENT}'" "SENT" "duplicate Outbox was not resent"
sleep 3
wait_sql_value "SELECT count(*) FROM mom_inbox_event WHERE event_id='${UPDATE_EVENT}'" "1" "duplicate Inbox identity changed"
wait_cache_absent

curl --fail --silent --show-error --header "$AUTH_HEADER" \
  "http://127.0.0.1:${SYSTEM_PORT}/api/system/parameters/${PARAMETER_KEY}" \
  > /dev/null
assert_cache_present
docker stop "$BROKER_CONTAINER" >/dev/null

UPDATE3_STATUS=$(curl --silent --output system-runtime-event-update-v3.json --write-out '%{http_code}' \
  --request PUT --header "$AUTH_HEADER" --header 'Content-Type: application/json' \
  --data '{"version":1,"valueType":"STRING","parameterValue":"v3","description":"S18 Broker outage"}' \
  "http://127.0.0.1:${SYSTEM_PORT}/api/system/admin/parameters/${PARAMETER_ID}")
[[ "$UPDATE3_STATUS" == "200" ]] || fail "Parameter update during Broker outage failed; HTTP ${UPDATE3_STATUS}"
OUTAGE_EVENT=$(wait_new_event "$PARAMETER_ID" "$UPDATE_EVENT")
for attempt in {1..60}; do
  OUTAGE_STATE=$(sql_value "SELECT CASE WHEN status='RETRY' AND retry_count >= 1 THEN 'ready' ELSE status END FROM mom_outbox_event WHERE event_id='${OUTAGE_EVENT}'")
  [[ "$OUTAGE_STATE" == "ready" ]] && break
  [[ "$attempt" != "60" ]] || fail "Broker outage event did not enter RETRY"
  sleep 1
done
[[ "$(sql_value "SELECT parameter_value FROM system_parameter WHERE id='${PARAMETER_ID}'")" == "v3" ]] \
  || fail "Database business fact was not committed during Broker outage"

docker start "$BROKER_CONTAINER" >/dev/null
wait_broker
wait_sql_value "SELECT status FROM mom_outbox_event WHERE event_id='${OUTAGE_EVENT}'" "SENT" "recovered Outbox was not sent"
wait_sql_value "SELECT count(*) FROM mom_inbox_event WHERE event_id='${OUTAGE_EVENT}' AND processed_at IS NOT NULL" "1" "recovered event was not consumed"
wait_cache_absent

POISON_EVENT="00000000-0000-0000-0000-00000000d118"
docker exec -i "$POSTGRES_CONTAINER" psql \
  -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 <<SQL
SET search_path TO ${POSTGRES_SCHEMA};
INSERT INTO mom_outbox_event (
  event_id, event_type, event_version, aggregate_type, aggregate_id,
  occurred_at, producer, correlation_id, payload_json)
VALUES (
  '${POISON_EVENT}', 'system.parameter.changed', 1, 'SystemParameter', '${PARAMETER_ID}',
  CURRENT_TIMESTAMP, 'mom-system-server', '${POISON_EVENT}', '{}');
SQL
wait_sql_value "SELECT status FROM mom_outbox_event WHERE event_id='${POISON_EVENT}'" "SENT" "poison Outbox was not sent"
wait_sql_value "SELECT count(*) FROM mom_inbox_event WHERE event_id='${POISON_EVENT}'" "1" "poison Inbox identity was not retained"

DLQ_TOPIC="%DLQ%${CONSUMER_GROUP}"
for attempt in {1..180}; do
  DLQ_OUTPUT=$(docker exec "$BROKER_CONTAINER" sh mqadmin topicStatus \
    -n "$ROCKETMQ_NAME_SERVER" -t "$DLQ_TOPIC" 2>/dev/null || true)
  if printf '%s\n' "$DLQ_OUTPUT" | awk 'NR > 1 && $3 ~ /^[0-9]+$/ && $4 ~ /^[0-9]+$/ && $4 > $3 { found=1 } END { exit !found }'; then
    printf '%s\n' "$DLQ_OUTPUT" > system-runtime-event-dlq-status.txt
    break
  fi
  [[ "$attempt" != "180" ]] || {
    printf '%s\n' "$DLQ_OUTPUT" > system-runtime-event-dlq-status.txt
    fail "poison event did not reach RocketMQ DLQ"
  }
  sleep 1
done

kill "$SYSTEM_PID"
wait "$SYSTEM_PID" 2>/dev/null || true
SYSTEM_PID=""
docker stop "$REDIS_CONTAINER" >/dev/null

SPRING_AUTOCONFIGURE_EXCLUDE="$SECURITY_EXCLUSIONS" \
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" POSTGRES_SCHEMA="$POSTGRES_SCHEMA" \
POSTGRES_USERNAME="$POSTGRES_USERNAME" POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
REDIS_HOST=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
NACOS_DISCOVERY_ENABLED=false MANAGEMENT_HEALTH_REDIS_ENABLED=false \
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=false \
MOM_RESOURCE_SERVER_ENABLED=false IAM_PERMISSION_REFERENCE_OAUTH2_ENABLED=false \
SYSTEM_RUNTIME_CACHE_ENABLED=true MOM_ENVIRONMENT="$ENVIRONMENT" \
SYSTEM_RUNTIME_EVENT_CONSUMER_ENABLED=false SYSTEM_STREAM_FUNCTION_DEFINITION= \
OUTBOX_PUBLISHER_ENABLED=false \
java -jar mom-system-platform/mom-system-server/target/mom-system-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$SYSTEM_PORT" \
  --mom.system.catalog.permission-reconciliation.enabled=false \
  --management.health.redis.enabled=false \
  > system-runtime-event-fallback-server.log 2>&1 &
SYSTEM_PID=$!
wait_http_200 "http://127.0.0.1:${SYSTEM_PORT}/actuator/health/readiness" \
  system-runtime-event-fallback-health.json "fallback System"
REDIS_OUTAGE_STATUS=$(curl --silent --output system-runtime-event-redis-outage.json --write-out '%{http_code}' \
  "http://127.0.0.1:${SYSTEM_PORT}/api/system/parameters/${PARAMETER_KEY}" || true)
[[ "$REDIS_OUTAGE_STATUS" == "200" ]] || fail "Runtime did not fall back to PostgreSQL during Redis outage; HTTP ${REDIS_OUTAGE_STATUS}"
jq --exit-status '.parameterValue == "v3" and .version == 2' \
  system-runtime-event-redis-outage.json >/dev/null || fail "Redis outage fallback response is invalid"

printf '%s\n' \
  'SYSTEM_RUNTIME_EVENT_SMOKE result=success jwt_audit=success http_business_write=success outbox=sent inbox=idempotent cache=invalidated duplicate=deduplicated broker_recovery=success redis_fallback=postgresql poison=dlq'
