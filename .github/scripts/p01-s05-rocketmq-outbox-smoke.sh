#!/usr/bin/env bash
set -Eeuo pipefail

POSTGRES_CONTAINER="mom-postgres-message-ci"
ROCKETMQ_NAMESRV_CONTAINER="mom-rocketmq-namesrv-ci"
ROCKETMQ_BROKER_CONTAINER="mom-rocketmq-broker-ci"
POSTGRES_DATABASE="mom_platform"
POSTGRES_USERNAME="mom"
POSTGRES_PASSWORD="mom"
POSTGRES_PORT="5434"
MDM_SCHEMA="mom_mdm"
INTEGRATION_SCHEMA="mom_integration"
MDM_PORT="20203"
INTEGRATION_PORT="20803"
ROCKETMQ_IMAGE="apache/rocketmq:5.3.2"
ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
EVENT_TOPIC="mom-domain-events-v1"
CONSUMER_GROUP="mom-integration-v1"
MDM_PID=""
INTEGRATION_PID=""
BROKER_CONFIG="$(pwd)/p01-s05-broker.conf"

SECURITY_EXCLUSIONS="org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  set +e
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && kill "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && wait "$MDM_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && wait "$INTEGRATION_PID" 2>/dev/null
  docker logs "$POSTGRES_CONTAINER" > p01-s05-postgresql.log 2>&1
  docker logs "$ROCKETMQ_NAMESRV_CONTAINER" > p01-s05-rocketmq-namesrv.log 2>&1
  docker logs "$ROCKETMQ_BROKER_CONTAINER" > p01-s05-rocketmq-broker.log 2>&1
  docker rm -f "$POSTGRES_CONTAINER" >/dev/null 2>&1
  docker rm -f "$ROCKETMQ_NAMESRV_CONTAINER" >/dev/null 2>&1
  docker rm -f "$ROCKETMQ_BROKER_CONTAINER" >/dev/null 2>&1
  rm -f "$BROKER_CONFIG"
}
trap cleanup EXIT

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

docker run --name "$POSTGRES_CONTAINER" \
  -e POSTGRES_DB="$POSTGRES_DATABASE" \
  -e POSTGRES_USER="$POSTGRES_USERNAME" \
  -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  -p "${POSTGRES_PORT}:5432" \
  -d postgres:17.7-alpine \
  postgres -c timezone=Asia/Tokyo

docker run --name "$ROCKETMQ_NAMESRV_CONTAINER" \
  --network host \
  -e JAVA_OPT_EXT="-Xms256m -Xmx256m -Xmn128m" \
  -d "$ROCKETMQ_IMAGE" \
  sh mqnamesrv

docker run --name "$ROCKETMQ_BROKER_CONTAINER" \
  --network host \
  -e NAMESRV_ADDR="$ROCKETMQ_NAME_SERVER" \
  -e JAVA_OPT_EXT="-Xms384m -Xmx384m -Xmn128m" \
  -v "$BROKER_CONFIG:/home/rocketmq/broker.conf:ro" \
  -d "$ROCKETMQ_IMAGE" \
  sh mqbroker -n "$ROCKETMQ_NAME_SERVER" -c /home/rocketmq/broker.conf

for attempt in {1..90}; do
  postgresql_ready=false
  rocketmq_ready=false
  if docker exec "$POSTGRES_CONTAINER" \
    pg_isready -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" >/dev/null 2>&1; then
    postgresql_ready=true
  fi
  if docker exec "$ROCKETMQ_BROKER_CONTAINER" \
    sh mqadmin clusterList -n "$ROCKETMQ_NAME_SERVER" 2>/dev/null \
      | grep --quiet "DefaultCluster"; then
    rocketmq_ready=true
  fi
  if [[ "$postgresql_ready" == "true" && "$rocketmq_ready" == "true" ]]; then
    break
  fi
  if [[ "$attempt" == "90" ]]; then
    echo "PostgreSQL or RocketMQ did not become ready"
    exit 1
  fi
  sleep 2
done

docker exec "$ROCKETMQ_BROKER_CONTAINER" \
  sh mqadmin updateTopic \
    -n "$ROCKETMQ_NAME_SERVER" \
    -c DefaultCluster \
    -t "$EVENT_TOPIC" >/dev/null

POSTGRES_HOST=127.0.0.1 \
POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" \
POSTGRES_SCHEMA="$INTEGRATION_SCHEMA" \
POSTGRES_USERNAME="$POSTGRES_USERNAME" \
POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
NACOS_DISCOVERY_ENABLED=false \
MOM_MESSAGE_CONSUMER_ENABLED=true \
MOM_MESSAGE_FUNCTION_DEFINITION=momDomainEventConsumer \
MOM_DOMAIN_EVENT_TOPIC="$EVENT_TOPIC" \
ROCKETMQ_CONSUMER_GROUP="$CONSUMER_GROUP" \
ROCKETMQ_NAME_SERVER="$ROCKETMQ_NAME_SERVER" \
ROCKETMQ_MAX_RECONSUME_TIMES=2 \
java -jar mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$INTEGRATION_PORT" \
  --spring.application.name=mom-integration-server \
  --management.health.redis.enabled=false \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  > p01-s05-integration-server.log 2>&1 &
INTEGRATION_PID=$!

POSTGRES_HOST=127.0.0.1 \
POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" \
POSTGRES_SCHEMA="$MDM_SCHEMA" \
POSTGRES_USERNAME="$POSTGRES_USERNAME" \
POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
NACOS_DISCOVERY_ENABLED=false \
MDM_OUTBOX_PROBE_ENABLED=true \
OUTBOX_PUBLISHER_ENABLED=true \
OUTBOX_FIXED_DELAY_MILLIS=250 \
OUTBOX_INITIAL_BACKOFF=1s \
OUTBOX_MAX_BACKOFF=5s \
MOM_DOMAIN_EVENT_TOPIC="$EVENT_TOPIC" \
ROCKETMQ_PRODUCER_GROUP=mom-mdm-outbox-v1 \
ROCKETMQ_NAME_SERVER="$ROCKETMQ_NAME_SERVER" \
java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$MDM_PORT" \
  --spring.application.name=mom-mdm-server \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  > p01-s05-mdm-server.log 2>&1 &
MDM_PID=$!

wait_for_http_200() {
  local url="$1"
  local output="$2"
  for attempt in {1..90}; do
    local status
    status=$(curl --silent --output "$output" --write-out '%{http_code}' "$url" || true)
    if [[ "$status" == "200" ]]; then
      return 0
    fi
    if [[ "$attempt" == "90" ]]; then
      echo "Application did not become healthy: $url HTTP $status"
      cat "$output" || true
      return 1
    fi
    sleep 2
  done
}

wait_for_sql_value() {
  local schema="$1"
  local sql="$2"
  local expected="$3"
  local description="$4"
  for attempt in {1..120}; do
    local actual
    actual=$(docker exec "$POSTGRES_CONTAINER" \
      psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc \
      "SET search_path TO ${schema}; ${sql}" 2>/dev/null | tail -n 1 | tr -d '[:space:]')
    if [[ "$actual" == "$expected" ]]; then
      return 0
    fi
    if [[ "$attempt" == "120" ]]; then
      echo "Timed out waiting for ${description}; expected=${expected}, actual=${actual}"
      return 1
    fi
    sleep 1
  done
}

wait_for_http_200 \
  "http://127.0.0.1:${INTEGRATION_PORT}/actuator/health" \
  p01-s05-integration-health.json
wait_for_http_200 \
  "http://127.0.0.1:${MDM_PORT}/actuator/health" \
  p01-s05-mdm-health.json

create_outbox_event() {
  local key="$1"
  local poison="$2"
  local response_file="$3"
  local status
  status=$(curl --silent --output "$response_file" \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --header "X-Correlation-Id: p01-s05-${key}" \
    --data "{\"probeKey\":\"${key}\",\"probeValue\":\"rocketmq-stream-ok\",\"poisonEvent\":${poison}}" \
    "http://127.0.0.1:${MDM_PORT}/internal/mdm/outbox-probes")
  [[ "$status" == "201" ]]
  jq --exit-status '(.probeId | type == "string") and (.eventId | type == "string")' "$response_file" >/dev/null
  jq --raw-output '.eventId' "$response_file"
}

normal_event_id=$(create_outbox_event \
  "p01-s05-normal" \
  false \
  p01-s05-normal-create.json)

wait_for_sql_value "$MDM_SCHEMA" \
  "select status from mom_outbox_event where event_id = '${normal_event_id}'" \
  "SENT" \
  "normal Outbox SENT"
wait_for_sql_value "$INTEGRATION_SCHEMA" \
  "select count(*) from mom_inbox_event where event_id = '${normal_event_id}' and processed_at is not null" \
  "1" \
  "normal Inbox processed"
wait_for_sql_value "$INTEGRATION_SCHEMA" \
  "select count(*) from technical_message_receipt where event_id = '${normal_event_id}'" \
  "1" \
  "normal technical receipt"

# 将同一 eventId 重新置为可发布，验证至少一次重复传输不会产生第二个 Inbox 或业务结果。
docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 -c \
  "SET search_path TO ${MDM_SCHEMA};
   UPDATE mom_outbox_event
   SET status = 'RETRY', next_attempt_at = CURRENT_TIMESTAMP, sent_at = NULL,
       lease_owner = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP
   WHERE event_id = '${normal_event_id}';" >/dev/null

wait_for_sql_value "$MDM_SCHEMA" \
  "select status from mom_outbox_event where event_id = '${normal_event_id}'" \
  "SENT" \
  "duplicate Outbox SENT"
sleep 3
wait_for_sql_value "$INTEGRATION_SCHEMA" \
  "select count(*) from mom_inbox_event where event_id = '${normal_event_id}'" \
  "1" \
  "duplicate Inbox remains one"
wait_for_sql_value "$INTEGRATION_SCHEMA" \
  "select count(*) from technical_message_receipt where event_id = '${normal_event_id}'" \
  "1" \
  "duplicate receipt remains one"

# Broker 中断期间业务请求仍只提交 PostgreSQL 本地事务，Outbox 进入 RETRY；Broker 恢复后自动补发。
docker stop "$ROCKETMQ_BROKER_CONTAINER" >/dev/null
outage_event_id=$(create_outbox_event \
  "p01-s05-broker-outage" \
  false \
  p01-s05-outage-create.json)
wait_for_sql_value "$MDM_SCHEMA" \
  "select case when status = 'RETRY' and retry_count >= 1 then 'ready' else 'waiting' end from mom_outbox_event where event_id = '${outage_event_id}'" \
  "ready" \
  "broker outage Outbox retry"

docker start "$ROCKETMQ_BROKER_CONTAINER" >/dev/null
for attempt in {1..90}; do
  if docker exec "$ROCKETMQ_BROKER_CONTAINER" \
    sh mqadmin clusterList -n "$ROCKETMQ_NAME_SERVER" 2>/dev/null \
      | grep --quiet "DefaultCluster"; then
    break
  fi
  if [[ "$attempt" == "90" ]]; then
    echo "RocketMQ Broker did not recover"
    exit 1
  fi
  sleep 2
done

wait_for_sql_value "$MDM_SCHEMA" \
  "select status from mom_outbox_event where event_id = '${outage_event_id}'" \
  "SENT" \
  "recovered Outbox SENT"
wait_for_sql_value "$INTEGRATION_SCHEMA" \
  "select count(*) from technical_message_receipt where event_id = '${outage_event_id}'" \
  "1" \
  "recovered event consumed"

poison_event_id=$(create_outbox_event \
  "p01-s05-poison" \
  true \
  p01-s05-poison-create.json)
wait_for_sql_value "$MDM_SCHEMA" \
  "select status from mom_outbox_event where event_id = '${poison_event_id}'" \
  "SENT" \
  "poison Outbox SENT"

DLQ_TOPIC="%DLQ%${CONSUMER_GROUP}"
for attempt in {1..180}; do
  dlq_output=$(docker exec "$ROCKETMQ_BROKER_CONTAINER" \
    sh mqadmin topicStatus -n "$ROCKETMQ_NAME_SERVER" -t "$DLQ_TOPIC" 2>/dev/null || true)
  if printf '%s\n' "$dlq_output" | awk 'NR > 1 && $4 ~ /^[0-9]+$/ && $3 ~ /^[0-9]+$/ && $4 > $3 { found=1 } END { exit !found }'; then
    printf '%s\n' "$dlq_output" > p01-s05-dlq-status.txt
    break
  fi
  if [[ "$attempt" == "180" ]]; then
    echo "Poison event did not reach RocketMQ DLQ"
    printf '%s\n' "$dlq_output" > p01-s05-dlq-status.txt
    exit 1
  fi
  sleep 1
done

wait_for_sql_value "$INTEGRATION_SCHEMA" \
  "select count(*) from mom_inbox_event where event_id = '${poison_event_id}'" \
  "0" \
  "poison Inbox transaction rolled back"
wait_for_sql_value "$INTEGRATION_SCHEMA" \
  "select count(*) from technical_message_receipt where event_id = '${poison_event_id}'" \
  "0" \
  "poison business result absent"

# 输出最终状态供失败诊断和 PR 验收留档。
docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -c \
  "SET search_path TO ${MDM_SCHEMA};
   SELECT event_id, event_type, status, retry_count, sent_at
   FROM mom_outbox_event ORDER BY created_at;" \
  > p01-s05-outbox-status.txt

docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -c \
  "SET search_path TO ${INTEGRATION_SCHEMA};
   SELECT event_id, consumer_name, received_at, processed_at
   FROM mom_inbox_event ORDER BY received_at;" \
  > p01-s05-inbox-status.txt
