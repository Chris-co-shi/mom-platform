#!/usr/bin/env bash
set -Eeuo pipefail

MDM_DB_CONTAINER=mom-p01-s06-mdm-postgres
INTEGRATION_DB_CONTAINER=mom-p01-s06-integration-postgres
SEATA_CONTAINER=mom-p01-s06-seata-server
MDM_DB_PORT=55432
INTEGRATION_DB_PORT=55433
MDM_MIGRATION_PORT=20211
INTEGRATION_MIGRATION_PORT=20811
MDM_PORT=20210
INTEGRATION_PORT=20810
MDM_PID=""
INTEGRATION_PID=""

SECURITY_EXCLUSIONS="org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  set +e
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && kill "$INTEGRATION_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && wait "$MDM_PID" 2>/dev/null
  [[ -n "$INTEGRATION_PID" ]] && wait "$INTEGRATION_PID" 2>/dev/null
  docker logs "$MDM_DB_CONTAINER" > p01-s06-mdm-postgresql.log 2>&1
  docker logs "$INTEGRATION_DB_CONTAINER" > p01-s06-integration-postgresql.log 2>&1
  docker logs "$SEATA_CONTAINER" > p01-s06-seata-server.log 2>&1
  docker rm -f "$MDM_DB_CONTAINER" >/dev/null 2>&1
  docker rm -f "$INTEGRATION_DB_CONTAINER" >/dev/null 2>&1
  docker rm -f "$SEATA_CONTAINER" >/dev/null 2>&1
}
trap cleanup EXIT

docker rm -f "$MDM_DB_CONTAINER" "$INTEGRATION_DB_CONTAINER" "$SEATA_CONTAINER" >/dev/null 2>&1 || true

docker run --name "$MDM_DB_CONTAINER" \
  -e POSTGRES_DB=mom_platform \
  -e POSTGRES_USER=mom \
  -e POSTGRES_PASSWORD=mom \
  -p ${MDM_DB_PORT}:5432 \
  -d postgres:17.7-alpine \
  postgres -c fsync=off -c timezone=Asia/Tokyo

docker run --name "$INTEGRATION_DB_CONTAINER" \
  -e POSTGRES_DB=mom_platform \
  -e POSTGRES_USER=mom \
  -e POSTGRES_PASSWORD=mom \
  -p ${INTEGRATION_DB_PORT}:5432 \
  -d postgres:17.7-alpine \
  postgres -c fsync=off -c timezone=Asia/Tokyo

docker run --name "$SEATA_CONTAINER" \
  -e SEATA_IP=127.0.0.1 \
  -e SEATA_PORT=8091 \
  -e STORE_MODE=file \
  -p 7091:7091 \
  -p 8091:8091 \
  -d apache/seata-server:2.5.0.jdk21

for attempt in {1..90}; do
  mdm_ready=false
  integration_ready=false
  seata_ready=false
  if docker exec "$MDM_DB_CONTAINER" pg_isready -U mom -d mom_platform >/dev/null 2>&1; then
    mdm_ready=true
  fi
  if docker exec "$INTEGRATION_DB_CONTAINER" pg_isready -U mom -d mom_platform >/dev/null 2>&1; then
    integration_ready=true
  fi
  if timeout 1 bash -c '</dev/tcp/127.0.0.1/8091' >/dev/null 2>&1; then
    seata_ready=true
  fi
  if [[ "$mdm_ready" == "true" && "$integration_ready" == "true" && "$seata_ready" == "true" ]]; then
    break
  fi
  if [[ "$attempt" == "90" ]]; then
    echo "PostgreSQL or Seata Server did not become ready"
    exit 1
  fi
  sleep 2
done

# Seata DataSourceProxy 在构造阶段会先检查 undo_log，因此全新数据库必须先完成迁移。
# 该阶段运行同一打包应用和同一 Flyway 脚本，但显式关闭 Seata 和技术接口；
# 完成后停止迁移实例，再启动启用数据源代理的正式验证实例。
POSTGRES_PORT=$INTEGRATION_DB_PORT \
POSTGRES_SCHEMA=mom_integration \
POSTGRES_APPLICATION_NAME=mom-integration-seata-migration-ci \
SEATA_ENABLED=false \
INTEGRATION_SEATA_AT_PROBE_ENABLED=false \
java -jar mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port=$INTEGRATION_MIGRATION_PORT \
  --spring.application.name=mom-integration-server \
  --spring.cloud.nacos.discovery.enabled=false \
  --management.health.redis.enabled=false \
  --seata.enabled=false \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  > p01-s06-integration-migration.log 2>&1 &
INTEGRATION_PID=$!

POSTGRES_PORT=$MDM_DB_PORT \
POSTGRES_SCHEMA=mom_mdm \
POSTGRES_APPLICATION_NAME=mom-mdm-seata-migration-ci \
SEATA_ENABLED=false \
MDM_SEATA_AT_PROBE_ENABLED=false \
java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port=$MDM_MIGRATION_PORT \
  --spring.application.name=mom-mdm-server \
  --spring.cloud.nacos.discovery.enabled=false \
  --seata.enabled=false \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  > p01-s06-mdm-migration.log 2>&1 &
MDM_PID=$!

for attempt in {1..90}; do
  mdm_migration_status=$(curl --silent --output p01-s06-mdm-migration-health.json --write-out '%{http_code}' \
    http://127.0.0.1:$MDM_MIGRATION_PORT/actuator/health || true)
  integration_migration_status=$(curl --silent --output p01-s06-integration-migration-health.json --write-out '%{http_code}' \
    http://127.0.0.1:$INTEGRATION_MIGRATION_PORT/actuator/health || true)
  if [[ "$mdm_migration_status" == "200" && "$integration_migration_status" == "200" ]]; then
    break
  fi
  if [[ "$attempt" == "90" ]]; then
    echo "MDM or Integration migration instance did not become ready"
    cat p01-s06-mdm-migration-health.json || true
    cat p01-s06-integration-migration-health.json || true
    exit 1
  fi
  sleep 2
done

mdm_undo_exists=$(docker exec "$MDM_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
  "select count(*) from information_schema.tables where table_schema='mom_mdm' and table_name='undo_log'")
integration_undo_exists=$(docker exec "$INTEGRATION_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
  "select count(*) from information_schema.tables where table_schema='mom_integration' and table_name='undo_log'")
[[ "$mdm_undo_exists" == "1" ]]
[[ "$integration_undo_exists" == "1" ]]

mdm_technical_table_count=$(docker exec "$MDM_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
  "select count(*) from information_schema.tables where table_schema='mom_mdm' and table_name in ('technical_data_probe', 'technical_seata_at_coordinator')")
integration_technical_table_count=$(docker exec "$INTEGRATION_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
  "select count(*) from information_schema.tables where table_schema='mom_integration' and table_name in ('technical_message_receipt', 'technical_seata_at_participant')")
[[ "$mdm_technical_table_count" == "0" ]]
[[ "$integration_technical_table_count" == "0" ]]

docker exec -i "$MDM_DB_CONTAINER" psql -U mom -d mom_platform -v ON_ERROR_STOP=1 \
  -v schema=mom_mdm -f - < .github/scripts/sql/mdm-phase01-technical-tables.sql
docker exec -i "$INTEGRATION_DB_CONTAINER" psql -U mom -d mom_platform -v ON_ERROR_STOP=1 \
  -v schema=mom_integration -f - < .github/scripts/sql/integration-phase01-technical-tables.sql

kill "$MDM_PID" "$INTEGRATION_PID"
wait "$MDM_PID" 2>/dev/null || true
wait "$INTEGRATION_PID" 2>/dev/null || true
MDM_PID=""
INTEGRATION_PID=""

POSTGRES_PORT=$INTEGRATION_DB_PORT \
POSTGRES_SCHEMA=mom_integration \
POSTGRES_APPLICATION_NAME=mom-integration-seata-ci \
SEATA_ENABLED=true \
SEATA_SERVER_ADDR=127.0.0.1:8091 \
INTEGRATION_SEATA_AT_PROBE_ENABLED=true \
java -jar mom-integration-platform/mom-integration-server/target/mom-integration-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port=$INTEGRATION_PORT \
  --spring.application.name=mom-integration-server \
  --spring.cloud.nacos.discovery.enabled=false \
  --management.health.redis.enabled=false \
  --seata.enabled=true \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  > p01-s06-integration-server.log 2>&1 &
INTEGRATION_PID=$!

POSTGRES_PORT=$MDM_DB_PORT \
POSTGRES_SCHEMA=mom_mdm \
POSTGRES_APPLICATION_NAME=mom-mdm-seata-ci \
SEATA_ENABLED=true \
SEATA_SERVER_ADDR=127.0.0.1:8091 \
MDM_SEATA_AT_PROBE_ENABLED=true \
java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port=$MDM_PORT \
  --spring.application.name=mom-mdm-server \
  --spring.cloud.nacos.discovery.enabled=false \
  --seata.enabled=true \
  --spring.cloud.openfeign.client.config.integrationSeataAtParticipantClient.url=http://127.0.0.1:$INTEGRATION_PORT \
  --spring.cloud.openfeign.client.config.mom-integration-server.url=http://127.0.0.1:$INTEGRATION_PORT \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  > p01-s06-mdm-server.log 2>&1 &
MDM_PID=$!

for attempt in {1..90}; do
  mdm_status=$(curl --silent --output p01-s06-mdm-health.json --write-out '%{http_code}' \
    http://127.0.0.1:$MDM_PORT/actuator/health || true)
  integration_status=$(curl --silent --output p01-s06-integration-health.json --write-out '%{http_code}' \
    http://127.0.0.1:$INTEGRATION_PORT/actuator/health || true)
  if [[ "$mdm_status" == "200" && "$integration_status" == "200" ]]; then
    break
  fi
  if [[ "$attempt" == "90" ]]; then
    echo "MDM or Integration did not become ready with Seata enabled"
    cat p01-s06-mdm-health.json || true
    cat p01-s06-integration-health.json || true
    exit 1
  fi
  sleep 2
done

mdm_count() {
  local key=$1
  docker exec "$MDM_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
    "select count(*) from mom_mdm.technical_seata_at_coordinator where transaction_key = '$key'"
}

integration_count() {
  local key=$1
  docker exec "$INTEGRATION_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
    "select count(*) from mom_integration.technical_seata_at_participant where transaction_key = '$key'"
}

wait_for_counts() {
  local key=$1
  local expected_mdm=$2
  local expected_integration=$3
  for attempt in {1..60}; do
    actual_mdm=$(mdm_count "$key")
    actual_integration=$(integration_count "$key")
    if [[ "$actual_mdm" == "$expected_mdm" && "$actual_integration" == "$expected_integration" ]]; then
      return 0
    fi
    sleep 1
  done
  echo "Unexpected final counts for $key: mdm=$(mdm_count "$key"), integration=$(integration_count "$key")"
  return 1
}

post_probe() {
  local body=$1
  local output=$2
  curl --silent --show-error --max-time 25 \
    --output "$output" \
    --write-out '%{http_code}' \
    --request POST \
    --header 'Content-Type: application/json' \
    --data "$body" \
    http://127.0.0.1:$MDM_PORT/internal/mdm/seata-at-probes || true
}

SUCCESS_KEY=p01-s06-success
success_status=$(post_probe \
  '{"transactionKey":"p01-s06-success","coordinatorValue":"mdm-commit","participantValue":"integration-commit","failParticipant":false,"failAfterParticipant":false}' \
  p01-s06-success-response.json)
[[ "$success_status" == "201" ]]
jq --exit-status '
  .transactionKey == "p01-s06-success"
  and .status == "COMMITTING"
  and (.coordinatorXid | length) > 0
  and .coordinatorXid == .participantXid
' p01-s06-success-response.json
wait_for_counts "$SUCCESS_KEY" 1 1

success_xid=$(jq -r '.coordinatorXid' p01-s06-success-response.json)
mdm_xid=$(docker exec "$MDM_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
  "select xid from mom_mdm.technical_seata_at_coordinator where transaction_key = '$SUCCESS_KEY'")
integration_xid=$(docker exec "$INTEGRATION_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
  "select xid from mom_integration.technical_seata_at_participant where transaction_key = '$SUCCESS_KEY'")
[[ "$success_xid" == "$mdm_xid" ]]
[[ "$success_xid" == "$integration_xid" ]]

GLOBAL_ROLLBACK_KEY=p01-s06-global-rollback
global_rollback_status=$(post_probe \
  '{"transactionKey":"p01-s06-global-rollback","coordinatorValue":"mdm-rollback","participantValue":"integration-rollback","failParticipant":false,"failAfterParticipant":true}' \
  p01-s06-global-rollback-response.json)
[[ "$global_rollback_status" =~ ^5[0-9][0-9]$ ]]
wait_for_counts "$GLOBAL_ROLLBACK_KEY" 0 0

PARTICIPANT_FAILURE_KEY=p01-s06-participant-failure
participant_failure_status=$(post_probe \
  '{"transactionKey":"p01-s06-participant-failure","coordinatorValue":"mdm-participant-failure","participantValue":"integration-failure","failParticipant":true,"failAfterParticipant":false}' \
  p01-s06-participant-failure-response.json)
[[ "$participant_failure_status" =~ ^5[0-9][0-9]$ ]]
wait_for_counts "$PARTICIPANT_FAILURE_KEY" 0 0

for attempt in {1..60}; do
  mdm_undo_count=$(docker exec "$MDM_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
    'select count(*) from mom_mdm.undo_log')
  integration_undo_count=$(docker exec "$INTEGRATION_DB_CONTAINER" psql -U mom -d mom_platform -Atc \
    'select count(*) from mom_integration.undo_log')
  if [[ "$mdm_undo_count" == "0" && "$integration_undo_count" == "0" ]]; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    echo "Seata undo_log was not cleaned: mdm=$mdm_undo_count integration=$integration_undo_count"
    exit 1
  fi
  sleep 1
done

docker stop "$SEATA_CONTAINER" >/dev/null

TC_OUTAGE_KEY=p01-s06-tc-outage
tc_outage_status=$(post_probe \
  '{"transactionKey":"p01-s06-tc-outage","coordinatorValue":"must-not-write","participantValue":"must-not-write","failParticipant":false,"failAfterParticipant":false}' \
  p01-s06-tc-outage-response.json)
[[ "$tc_outage_status" != "201" ]]
wait_for_counts "$TC_OUTAGE_KEY" 0 0

printf 'success=%s\nglobalRollback=%s\nparticipantFailure=%s\ntcOutage=%s\n' \
  "$success_status" \
  "$global_rollback_status" \
  "$participant_failure_status" \
  "$tc_outage_status" \
  > p01-s06-status.txt
