#!/usr/bin/env bash
set -Eeuo pipefail

POSTGRES_CONTAINER="mom-postgres-ci"
POSTGRES_DATABASE="mom_platform"
POSTGRES_USERNAME="mom"
POSTGRES_PASSWORD="mom"
POSTGRES_SCHEMA="mom_mdm"
MDM_PORT="20201"
PROBE_KEY="p01-s04-packaged-application"
MDM_PID=""

# 仅排除当前阶段尚未建立正式策略的安全自动配置；数据源与 Flyway 必须真实启用。
SECURITY_EXCLUSIONS="org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  set +e
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  wait "$MDM_PID" 2>/dev/null
  docker logs "$POSTGRES_CONTAINER" > postgresql-server.log 2>&1
  docker rm -f "$POSTGRES_CONTAINER" >/dev/null 2>&1
}
trap cleanup EXIT

docker run --name "$POSTGRES_CONTAINER" \
  -e POSTGRES_DB="$POSTGRES_DATABASE" \
  -e POSTGRES_USER="$POSTGRES_USERNAME" \
  -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  -p 5432:5432 \
  -d postgres:17.7-alpine \
  postgres -c timezone=Asia/Tokyo

for attempt in {1..60}; do
  if docker exec "$POSTGRES_CONTAINER" \
    pg_isready -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    echo "PostgreSQL did not become ready"
    exit 1
  fi
  sleep 2
done

POSTGRES_HOST=127.0.0.1 \
POSTGRES_PORT=5432 \
POSTGRES_DATABASE="$POSTGRES_DATABASE" \
POSTGRES_SCHEMA="$POSTGRES_SCHEMA" \
POSTGRES_USERNAME="$POSTGRES_USERNAME" \
POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
NACOS_DISCOVERY_ENABLED=false \
java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$MDM_PORT" \
  --spring.application.name=mom-mdm-server \
  --mom.mdm.data-probe.enabled=true \
  --spring.autoconfigure.exclude="$SECURITY_EXCLUSIONS" \
  > mdm-postgresql-server.log 2>&1 &
MDM_PID=$!

for attempt in {1..60}; do
  health_status=$(curl --silent --output mdm-postgresql-health.json \
    --write-out '%{http_code}' \
    "http://127.0.0.1:${MDM_PORT}/actuator/health" || true)
  if [[ "$health_status" == "200" ]]; then
    break
  fi
  if [[ "$attempt" == "60" ]]; then
    echo "MDM PostgreSQL application did not become healthy; HTTP ${health_status}"
    cat mdm-postgresql-health.json || true
    exit 1
  fi
  sleep 2
done

create_status=$(curl --silent --output mdm-data-probe-create.json \
  --write-out '%{http_code}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --data "{\"probeKey\":\"${PROBE_KEY}\",\"probeValue\":\"postgresql-17.7-ok\"}" \
  "http://127.0.0.1:${MDM_PORT}/internal/mdm/data-probes")
[[ "$create_status" == "201" ]]

jq --exit-status --arg probeKey "$PROBE_KEY" '
  (.id | type == "string")
  and (.id | test("^[0-9]{1,19}$"))
  and .probeKey == $probeKey
  and .probeValue == "postgresql-17.7-ok"
  and .version == 0
  and .deleted == false
  and (.createdAt | endswith("Z"))
  and (.updatedAt | endswith("Z"))
' mdm-data-probe-create.json

read_status=$(curl --silent --output mdm-data-probe-read.json \
  --write-out '%{http_code}' \
  "http://127.0.0.1:${MDM_PORT}/internal/mdm/data-probes/${PROBE_KEY}")
[[ "$read_status" == "200" ]]

jq --exit-status --arg probeKey "$PROBE_KEY" '
  (.id | type == "string")
  and .probeKey == $probeKey
  and .probeValue == "postgresql-17.7-ok"
  and .version == 0
  and .deleted == false
' mdm-data-probe-read.json

metrics_status=$(curl --silent --output mdm-postgresql-prometheus.txt \
  --write-out '%{http_code}' \
  "http://127.0.0.1:${MDM_PORT}/actuator/prometheus")
[[ "$metrics_status" == "200" ]]
grep --extended-regexp --quiet '^jdbc_connections_max(\{| )' mdm-postgresql-prometheus.txt
grep --extended-regexp --quiet '^hikaricp_connections_max(\{| )' mdm-postgresql-prometheus.txt

server_timezone=$(docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc 'show timezone')
[[ "$server_timezone" == "Asia/Tokyo" ]]

application_connection_count=$(docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc \
  "select count(*) from pg_stat_activity where application_name = 'mom-mdm-server'")
[[ "$application_connection_count" -ge 1 ]]
[[ "$application_connection_count" -le 5 ]]

migration_count=$(docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc \
  "select count(*) from ${POSTGRES_SCHEMA}.flyway_schema_history where success = true and version in ('1', '2')")
[[ "$migration_count" == "2" ]]

id_column_definition=$(docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc \
  "select data_type || ':' || character_maximum_length from information_schema.columns where table_schema = '${POSTGRES_SCHEMA}' and table_name = 'technical_data_probe' and column_name = 'id'")
[[ "$id_column_definition" == "character varying:19" ]]

probe_count=$(docker exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -tAc \
  "select count(*) from ${POSTGRES_SCHEMA}.technical_data_probe where probe_key = '${PROBE_KEY}' and deleted = false")
[[ "$probe_count" == "1" ]]

# 数据库不可用时读取请求不得返回 2xx，验证数据访问默认 fail-closed。
docker stop "$POSTGRES_CONTAINER" >/dev/null
database_failure_status=$(curl --silent --max-time 12 \
  --output mdm-postgresql-failure.json \
  --write-out '%{http_code}' \
  "http://127.0.0.1:${MDM_PORT}/internal/mdm/data-probes/${PROBE_KEY}" || true)
[[ "$database_failure_status" =~ ^5[0-9][0-9]$ ]]
