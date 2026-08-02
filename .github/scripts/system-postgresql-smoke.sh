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
    jq --null-input --arg reason "$FAILURE_REASON" --arg container "$POSTGRES_CONTAINER" \
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
  -e POSTGRES_DB="$POSTGRES_DATABASE" -e POSTGRES_USER="$POSTGRES_USERNAME" \
  -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" -p 127.0.0.1::5432 \
  -d postgres:17.7-alpine postgres -c timezone=Asia/Tokyo >/dev/null
POSTGRES_PORT=$(docker port "$POSTGRES_CONTAINER" 5432/tcp | awk -F: 'NR == 1 {print $NF}')
[[ -n "$POSTGRES_PORT" ]] || { FAILURE_REASON="cannot resolve PostgreSQL port"; exit 1; }

for attempt in {1..60}; do
  docker exec "$POSTGRES_CONTAINER" pg_isready -U "$POSTGRES_USERNAME" \
    -d "$POSTGRES_DATABASE" >/dev/null 2>&1 && break
  [[ "$attempt" != "60" ]] || { FAILURE_REASON="PostgreSQL did not become ready"; exit 1; }
  sleep 2
done

POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" POSTGRES_SCHEMA="$POSTGRES_SCHEMA" \
POSTGRES_USERNAME="$POSTGRES_USERNAME" POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
NACOS_DISCOVERY_ENABLED=false MANAGEMENT_HEALTH_REDIS_ENABLED=false \
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=false TZ=UTC \
java -jar mom-system-platform/mom-system-server/target/mom-system-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$SYSTEM_PORT" > system-postgresql-server.log 2>&1 &
SYSTEM_PID=$!

for attempt in {1..60}; do
  status=$(curl --silent --output system-postgresql-health.json --write-out '%{http_code}' \
    "http://127.0.0.1:${SYSTEM_PORT}/actuator/health/readiness" || true)
  if [[ "$status" == "200" ]] && jq --exit-status '.status == "UP"' \
      system-postgresql-health.json >/dev/null; then
    break
  fi
  [[ "$attempt" != "60" ]] || {
    FAILURE_REASON="System readiness did not become UP; HTTP ${status}"; exit 1;
  }
  sleep 2
done

docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" \
  -v ON_ERROR_STOP=1 -Atc "
SELECT 'schema=' || count(*) FROM information_schema.schemata WHERE schema_name='${POSTGRES_SCHEMA}';
SELECT 'flyway_version=' || max(version::integer) FROM ${POSTGRES_SCHEMA}.flyway_schema_history WHERE success=true;
SELECT 'parameter=' || count(*) FROM information_schema.tables WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name='system_parameter';
SELECT 'dictionary=' || count(*) FROM information_schema.tables WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name IN ('system_dictionary','system_dictionary_item');
SELECT 'i18n=' || count(*) FROM information_schema.tables WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name IN ('system_i18n_resource','system_i18n_message','system_i18n_release');
SELECT 'preference=' || count(*) FROM information_schema.tables WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name IN ('system_user_preference','system_user_view_setting');
SELECT 'catalog=' || count(*) FROM information_schema.tables WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name IN ('system_application','system_navigation_item','system_catalog_release');
SELECT 'outbox=' || count(*) FROM information_schema.tables WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name='mom_outbox_event';
SELECT 'inbox=' || count(*) FROM information_schema.tables WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name='mom_inbox_event';
SELECT 'preference_jsonb=' || count(*) FROM information_schema.columns WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name='system_user_view_setting' AND column_name IN ('columns_json','sort_json','filters_json') AND data_type='jsonb';
SELECT 'i18n_jsonb=' || count(*) FROM information_schema.columns WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name='system_i18n_release' AND column_name='messages_json' AND data_type='jsonb';
SELECT 'catalog_jsonb=' || count(*) FROM information_schema.columns WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name='system_catalog_release' AND column_name='snapshot_json' AND data_type='jsonb';
SELECT 'catalog_unique=' || count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='${POSTGRES_SCHEMA}' AND c.conname IN ('uk_system_application_code','uk_system_navigation_item_route','uk_system_catalog_release_application_version') AND c.contype='u';
SELECT 'catalog_immutable_trigger=' || count(*) FROM pg_trigger tr JOIN pg_class t ON t.oid=tr.tgrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='${POSTGRES_SCHEMA}' AND t.relname='system_catalog_release' AND tr.tgname='trg_system_catalog_release_immutable' AND NOT tr.tgisinternal;
SELECT 'outbox_claim_index=' || count(*) FROM pg_indexes WHERE schemaname='${POSTGRES_SCHEMA}' AND tablename='mom_outbox_event' AND indexname='ix_mom_outbox_event_claim';
SELECT 'inbox_identity=' || count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='${POSTGRES_SCHEMA}' AND t.relname='mom_inbox_event' AND c.conname='pk_mom_inbox_event' AND c.contype='p' AND pg_get_constraintdef(c.oid) LIKE '%event_id, consumer_name%';
SELECT 'base_entity_deleted=' || count(*) FROM information_schema.columns WHERE table_schema='${POSTGRES_SCHEMA}' AND table_name IN ('system_parameter','system_dictionary','system_dictionary_item','system_i18n_resource','system_i18n_message','system_i18n_release') AND column_name='deleted' AND data_type='boolean' AND is_nullable='NO';
SELECT 'cross_schema_fk=' || count(*) FROM pg_constraint c JOIN pg_class s ON s.oid=c.conrelid JOIN pg_namespace sn ON sn.oid=s.relnamespace JOIN pg_class t ON t.oid=c.confrelid JOIN pg_namespace tn ON tn.oid=t.relnamespace WHERE c.contype='f' AND sn.nspname='${POSTGRES_SCHEMA}' AND tn.nspname<>'${POSTGRES_SCHEMA}';
SELECT 'business_fk=' || count(*) FROM pg_constraint c JOIN pg_class s ON s.oid=c.conrelid JOIN pg_namespace sn ON sn.oid=s.relnamespace WHERE c.contype='f' AND sn.nspname='${POSTGRES_SCHEMA}';
" > system-postgresql-schema.txt

for expected in schema=1 flyway_version=9 parameter=1 dictionary=2 i18n=3 preference=2 \
  catalog=3 outbox=1 inbox=1 preference_jsonb=3 i18n_jsonb=1 catalog_jsonb=1 \
  catalog_unique=3 catalog_immutable_trigger=1 outbox_claim_index=1 inbox_identity=1 \
  base_entity_deleted=6 cross_schema_fk=0 business_fk=0; do
  grep --fixed-strings --quiet "$expected" system-postgresql-schema.txt || {
    FAILURE_REASON="missing schema evidence: $expected"; exit 1;
  }
done

connections=$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USERNAME" \
  -d "$POSTGRES_DATABASE" -tAc \
  "select count(*) from pg_stat_activity where application_name='mom-system-server'")
[[ "$connections" -ge 1 && "$connections" -le 5 ]]
[[ "$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USERNAME" \
  -d "$POSTGRES_DATABASE" -tAc 'show timezone')" == "Asia/Tokyo" ]]

echo "SYSTEM_POSTGRESQL_SMOKE result=success flyway=9 outbox=1 inbox=1 business_fk=0 cross_schema_fk=0 readiness=UP"

POSTGRES_CONTAINER="$POSTGRES_CONTAINER" POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" POSTGRES_USERNAME="$POSTGRES_USERNAME" \
POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
  bash .github/scripts/system-iam-client-credentials-smoke.sh
