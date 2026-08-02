#!/usr/bin/env bash
set -Eeuo pipefail

POSTGRES_CONTAINER="mom-mdm-postgresql-smoke-${GITHUB_RUN_ID:-local}-$$"
POSTGRES_DATABASE="mom_platform"
POSTGRES_USERNAME="mom"
POSTGRES_PASSWORD="mom"
POSTGRES_SCHEMA="mom_mdm"
MDM_PORT="20201"
MDM_PID=""
POSTGRES_PORT=""
BOOTSTRAP_EXCLUSIONS="org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  set +e
  [[ -n "$MDM_PID" ]] && kill "$MDM_PID" 2>/dev/null
  [[ -n "$MDM_PID" ]] && wait "$MDM_PID" 2>/dev/null
  docker logs "$POSTGRES_CONTAINER" > postgresql-server.log 2>&1 || true
  docker rm -f "$POSTGRES_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run --name "$POSTGRES_CONTAINER" \
  -e POSTGRES_DB="$POSTGRES_DATABASE" \
  -e POSTGRES_USER="$POSTGRES_USERNAME" \
  -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  -p 127.0.0.1::5432 \
  -d postgres:17.7-alpine \
  postgres -c timezone=Asia/Tokyo >/dev/null
POSTGRES_PORT="$(docker port "$POSTGRES_CONTAINER" 5432/tcp | awk -F: 'NR == 1 {print $NF}')"
[[ -n "$POSTGRES_PORT" ]]

for attempt in {1..60}; do
  docker exec "$POSTGRES_CONTAINER" pg_isready -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" >/dev/null 2>&1 && break
  [[ "$attempt" == "60" ]] && echo "PostgreSQL did not become ready" >&2 && exit 1
  sleep 2
done

POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" POSTGRES_SCHEMA="$POSTGRES_SCHEMA" \
POSTGRES_USERNAME="$POSTGRES_USERNAME" POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
NACOS_DISCOVERY_ENABLED=false MANAGEMENT_HEALTH_REDIS_ENABLED=false \
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=false \
java -jar mom-mdm-platform/mom-mdm-server/target/mom-mdm-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$MDM_PORT" \
  --spring.autoconfigure.exclude="$BOOTSTRAP_EXCLUSIONS" \
  > mdm-postgresql-server.log 2>&1 &
MDM_PID=$!

for attempt in {1..60}; do
  status="$(curl --silent --output mdm-postgresql-health.json --write-out '%{http_code}' \
    "http://127.0.0.1:${MDM_PORT}/actuator/health/readiness" || true)"
  if [[ "$status" == "200" ]] && jq --exit-status '.status == "UP"' mdm-postgresql-health.json >/dev/null; then break; fi
  [[ "$attempt" == "60" ]] && echo "MDM readiness did not become UP" >&2 && exit 1
  sleep 2
done

docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -Atc "
  select 'schema=' || count(*) from information_schema.schemata where schema_name='${POSTGRES_SCHEMA}';
  select 'retirement_migration=' || count(*) from ${POSTGRES_SCHEMA}.flyway_schema_history where success=true and version='101';
  select 'technical_table_count=' || count(*) from information_schema.tables
   where table_schema='${POSTGRES_SCHEMA}' and table_name in
   ('technical_data_probe','mom_outbox_event','technical_seata_at_coordinator','undo_log');
  select 'business_fk=' || count(*) from pg_constraint c
   join pg_class t on t.oid=c.conrelid join pg_namespace n on n.oid=t.relnamespace
   where c.contype='f' and n.nspname='${POSTGRES_SCHEMA}';
" > mdm-postgresql-schema.txt

grep --fixed-strings --quiet 'schema=1' mdm-postgresql-schema.txt
grep --fixed-strings --quiet 'retirement_migration=1' mdm-postgresql-schema.txt
grep --fixed-strings --quiet 'technical_table_count=0' mdm-postgresql-schema.txt
grep --fixed-strings --quiet 'business_fk=0' mdm-postgresql-schema.txt

for retired_path in \
  /internal/mdm/probe \
  /internal/mdm/data-probes/retired \
  /internal/mdm/outbox-probes \
  /internal/mdm/seata-at-probes; do
  retired_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
    "http://127.0.0.1:${MDM_PORT}${retired_path}" || true)"
  [[ "$retired_status" == "404" ]]
done

metrics_status="$(curl --silent --output mdm-postgresql-prometheus.txt --write-out '%{http_code}' \
  "http://127.0.0.1:${MDM_PORT}/actuator/prometheus")"
[[ "$metrics_status" == "200" ]]
grep --extended-regexp --quiet '^jdbc_connections_max(\{| )' mdm-postgresql-prometheus.txt
grep --extended-regexp --quiet '^hikaricp_connections_max(\{| )' mdm-postgresql-prometheus.txt

echo "MDM_POSTGRESQL_SMOKE result=success retirement_migration=101 technical_tables=0 readiness=UP"
