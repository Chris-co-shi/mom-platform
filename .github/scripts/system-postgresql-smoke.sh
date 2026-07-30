#!/usr/bin/env bash
set -Eeuo pipefail

POSTGRES_CONTAINER="mom-system-postgresql-smoke-${$}-${RANDOM}"
POSTGRES_DATABASE="mom_platform"
POSTGRES_USERNAME="mom"
POSTGRES_PASSWORD="mom"
POSTGRES_SCHEMA="mom_system"
SYSTEM_PORT="20301"
SYSTEM_PID=""
POSTGRES_PORT=""
FAILURE_REASON="system PostgreSQL smoke failed"

cleanup() {
  exit_code=$?
  set +e
  if [[ "$exit_code" -ne 0 ]]; then
    jq --null-input \
      --arg reason "$FAILURE_REASON" \
      --arg container "$POSTGRES_CONTAINER" \
      --arg port "$POSTGRES_PORT" \
      '{reason: $reason, container: $container, postgresPort: $port}' \
      > system-postgresql-failure.json
  fi
  [[ -n "$SYSTEM_PID" ]] && kill "$SYSTEM_PID" 2>/dev/null
  [[ -n "$SYSTEM_PID" ]] && wait "$SYSTEM_PID" 2>/dev/null
  docker logs --tail 200 "$POSTGRES_CONTAINER" > system-postgresql-container.log 2>&1
  docker rm -f "$POSTGRES_CONTAINER" >/dev/null 2>&1
  exit "$exit_code"
}
trap cleanup EXIT

docker run --name "$POSTGRES_CONTAINER" \
  -e POSTGRES_DB="$POSTGRES_DATABASE" \
  -e POSTGRES_USER="$POSTGRES_USERNAME" \
  -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  -p 127.0.0.1::5432 \
  -d postgres:17.7-alpine \
  postgres -c timezone=Asia/Tokyo >/dev/null

POSTGRES_PORT=$(docker port "$POSTGRES_CONTAINER" 5432/tcp | awk -F: 'NR == 1 {print $NF}')
if [[ -z "$POSTGRES_PORT" ]]; then
  FAILURE_REASON="cannot resolve dynamic PostgreSQL host port"
  exit 1
fi

for attempt in {1..60}; do
  if docker exec "$POSTGRES_CONTAINER" \
    pg_isready -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    FAILURE_REASON="PostgreSQL did not become ready"
    exit 1
  fi
  sleep 2
done

POSTGRES_HOST=127.0.0.1 \
POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" \
POSTGRES_SCHEMA="$POSTGRES_SCHEMA" \
POSTGRES_USERNAME="$POSTGRES_USERNAME" \
POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
NACOS_DISCOVERY_ENABLED=false \
MANAGEMENT_HEALTH_REDIS_ENABLED=false \
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false \
MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=false \
TZ=UTC \
java -jar mom-system-platform/mom-system-server/target/mom-system-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$SYSTEM_PORT" \
  > system-postgresql-server.log 2>&1 &
SYSTEM_PID=$!

for attempt in {1..60}; do
  health_status=$(curl --silent --output system-postgresql-health.json \
    --write-out '%{http_code}' \
    "http://127.0.0.1:${SYSTEM_PORT}/actuator/health/readiness" || true)
  if [[ "$health_status" == "200" ]]; then
    if jq --exit-status '.status == "UP"' system-postgresql-health.json >/dev/null; then
      break
    fi
  fi
  if [[ "$attempt" == "60" ]]; then
    FAILURE_REASON="System readiness did not become UP; HTTP ${health_status}"
    exit 1
  fi
  sleep 2
done

docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 -Atc "
    SELECT 'schema=' || count(*)
      FROM information_schema.schemata
     WHERE schema_name = '${POSTGRES_SCHEMA}';
    SELECT 'migrations_v1_v2=' || count(*)
      FROM ${POSTGRES_SCHEMA}.flyway_schema_history
     WHERE version IN ('1', '2') AND success = true;
    SELECT 'system_parameter=' || count(*)
      FROM information_schema.tables
     WHERE table_schema = '${POSTGRES_SCHEMA}' AND table_name = 'system_parameter';
    SELECT 'system_dictionary_tables=' || count(*)
      FROM information_schema.tables
     WHERE table_schema = '${POSTGRES_SCHEMA}'
       AND table_name IN ('system_dictionary', 'system_dictionary_item');
    SELECT 'cross_schema_fk=' || count(*)
      FROM pg_constraint constraint_row
      JOIN pg_class source_table ON source_table.oid = constraint_row.conrelid
      JOIN pg_namespace source_schema ON source_schema.oid = source_table.relnamespace
      JOIN pg_class target_table ON target_table.oid = constraint_row.confrelid
      JOIN pg_namespace target_schema ON target_schema.oid = target_table.relnamespace
     WHERE constraint_row.contype = 'f'
       AND source_schema.nspname = '${POSTGRES_SCHEMA}'
       AND target_schema.nspname <> '${POSTGRES_SCHEMA}';
  " > system-postgresql-schema.txt

grep --fixed-strings --quiet 'schema=1' system-postgresql-schema.txt
grep --fixed-strings --quiet 'migrations_v1_v2=2' system-postgresql-schema.txt
grep --fixed-strings --quiet 'system_parameter=1' system-postgresql-schema.txt
grep --fixed-strings --quiet 'system_dictionary_tables=2' system-postgresql-schema.txt
grep --fixed-strings --quiet 'cross_schema_fk=0' system-postgresql-schema.txt

application_connection_count=$(docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc \
  "select count(*) from pg_stat_activity where application_name = 'mom-system-server'")
[[ "$application_connection_count" -ge 1 ]]
[[ "$application_connection_count" -le 5 ]]

server_timezone=$(docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc 'show timezone')
[[ "$server_timezone" == "Asia/Tokyo" ]]
