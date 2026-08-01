#!/usr/bin/env bash
set -Eeuo pipefail

: "${POSTGRES_CONTAINER:?POSTGRES_CONTAINER is required}"
: "${POSTGRES_PORT:?POSTGRES_PORT is required}"
: "${POSTGRES_DATABASE:?POSTGRES_DATABASE is required}"
: "${POSTGRES_USERNAME:?POSTGRES_USERNAME is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"

IAM_PORT="20181"
SYSTEM_INTEGRATION_PORT="20302"
IAM_PID=""
SYSTEM_INTEGRATION_PID=""
KEY_DIR="$(mktemp -d)"
IAM_LOG="${KEY_DIR}/iam.log"
SYSTEM_LOG="${KEY_DIR}/system.log"
TOKEN_RESPONSE="${KEY_DIR}/token.json"
VALIDATION_RESPONSE="${KEY_DIR}/validation.json"
CLIENT_ID="mom-system-server"
CLIENT_SECRET="s18-system-client-secret-0123456789"
CLIENT_SCOPE="iam.permission-reference.read"
REFRESH_PEPPER="s18-refresh-pepper-012345678901234567890123456789"
FAILURE_REASON="IAM-System client_credentials smoke failed"

cleanup() {
  exit_code=$?
  set +e
  [[ -n "$SYSTEM_INTEGRATION_PID" ]] && kill "$SYSTEM_INTEGRATION_PID" 2>/dev/null
  [[ -n "$SYSTEM_INTEGRATION_PID" ]] && wait "$SYSTEM_INTEGRATION_PID" 2>/dev/null
  [[ -n "$IAM_PID" ]] && kill "$IAM_PID" 2>/dev/null
  [[ -n "$IAM_PID" ]] && wait "$IAM_PID" 2>/dev/null
  if [[ "$exit_code" -ne 0 ]]; then
    {
      echo
      echo "===== IAM-System client_credentials smoke failure ====="
      echo "reason=${FAILURE_REASON}"
      echo "----- IAM log -----"
      tail -n 200 "$IAM_LOG" 2>/dev/null
      echo "----- System integration log -----"
      tail -n 200 "$SYSTEM_LOG" 2>/dev/null
    } >> system-postgresql-server.log
  fi
  rm -rf "$KEY_DIR"
  exit "$exit_code"
}
trap cleanup EXIT

wait_readiness() {
  local port="$1"
  local output="$2"
  local name="$3"
  local status=""
  for attempt in {1..90}; do
    status=$(curl --silent --output "$output" --write-out '%{http_code}' \
      "http://127.0.0.1:${port}/actuator/health/readiness" || true)
    if [[ "$status" == "200" ]] && jq --exit-status '.status == "UP"' "$output" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  FAILURE_REASON="${name} readiness did not become UP; HTTP ${status}"
  return 1
}

bash scripts/codex-mvn-test.sh \
  -pl mom-iam-platform/mom-iam-server \
  -am -DskipTests package

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "${KEY_DIR}/iam-private.pem" >/dev/null 2>&1
openssl pkey -in "${KEY_DIR}/iam-private.pem" -pubout \
  -out "${KEY_DIR}/iam-public.pem" >/dev/null 2>&1

POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" POSTGRES_SCHEMA=mom_iam \
POSTGRES_USERNAME="$POSTGRES_USERNAME" POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
NACOS_DISCOVERY_ENABLED=false MANAGEMENT_HEALTH_REDIS_ENABLED=false \
OTEL_METRICS_EXPORT_ENABLED=false OTEL_TRACING_EXPORT_ENABLED=false \
IAM_ISSUER="http://127.0.0.1:${IAM_PORT}" \
IAM_JWK_PRIVATE_KEY="file:${KEY_DIR}/iam-private.pem" \
IAM_JWK_PUBLIC_KEY="file:${KEY_DIR}/iam-public.pem" \
IAM_JWK_KEY_ID=s18-client-credentials-smoke \
IAM_SYSTEM_CLIENT_ENABLED=true IAM_SYSTEM_CLIENT_ID="$CLIENT_ID" \
IAM_SYSTEM_CLIENT_SECRET="$CLIENT_SECRET" IAM_SYSTEM_CLIENT_SCOPE="$CLIENT_SCOPE" \
IAM_REFRESH_HMAC_PEPPER="$REFRESH_PEPPER" \
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$IAM_PORT" > "$IAM_LOG" 2>&1 &
IAM_PID=$!
wait_readiness "$IAM_PORT" "${KEY_DIR}/iam-health.json" "IAM"

FAILURE_REASON="IAM token endpoint request failed"
curl --fail --silent --show-error \
  --user "${CLIENT_ID}:${CLIENT_SECRET}" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode "scope=${CLIENT_SCOPE}" \
  "http://127.0.0.1:${IAM_PORT}/oauth2/token" > "$TOKEN_RESPONSE"
ACCESS_TOKEN=$(jq --raw-output '.access_token // empty' "$TOKEN_RESPONSE")
[[ -n "$ACCESS_TOKEN" ]] || {
  FAILURE_REASON="IAM token endpoint did not return access_token"; exit 1;
}
jq --exit-status --arg scope "$CLIENT_SCOPE" \
  '.token_type == "Bearer" and ((.scope // "") | contains($scope)) and (.refresh_token | not)' \
  "$TOKEN_RESPONSE" >/dev/null || {
  FAILURE_REASON="IAM service token response contract is invalid"; exit 1;
}

FAILURE_REASON="IAM permission reference endpoint request failed"
curl --fail --silent --show-error \
  --header "Authorization: Bearer ${ACCESS_TOKEN}" \
  --header 'Content-Type: application/json' \
  --data '{"permissionCodes":["system:catalog:read","missing:reference:read"]}' \
  "http://127.0.0.1:${IAM_PORT}/api/iam/internal/permission-references/validate" \
  > "$VALIDATION_RESPONSE"
jq --exit-status '
  ([.results[] | select(.permissionCode == "system:catalog:read" and .status == "ENABLED")] | length) == 1
  and ([.results[] | select(.permissionCode == "missing:reference:read" and .status == "UNKNOWN")] | length) == 1
' "$VALIDATION_RESPONSE" >/dev/null || {
  FAILURE_REASON="IAM permission reference validation response is invalid"; exit 1;
}

SNAPSHOT_JSON=$(jq --compact-output --null-input '
  {
    snapshotSchemaVersion: 1,
    applicationCode: "iam",
    applicationType: "PLATFORM",
    routeContractVersion: 1,
    i18nResourceCode: "mom-web",
    i18nMessageKey: "mom.menu.iam",
    iconKey: null,
    channels: [
      {
        clientChannel: "WEB",
        navigation: [
          {
            routeKey: "iam.catalog",
            navigationType: "ROUTE",
            i18nResourceCode: "mom-web",
            i18nMessageKey: "mom.menu.iam.catalog",
            permissionCode: "system:catalog:read",
            iconKey: null,
            visibleInMenu: true,
            visibleInBreadcrumb: true,
            visibleInTab: true,
            keepAlive: false,
            children: []
          }
        ]
      }
    ]
  }
')
FAILURE_REASON="PostgreSQL could not normalize the Catalog snapshot JSON"
NORMALIZED_SNAPSHOT=$(docker exec -i "$POSTGRES_CONTAINER" psql \
  -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" \
  -v ON_ERROR_STOP=1 -v "snapshot=${SNAPSHOT_JSON}" -At <<'SQL'
SELECT :'snapshot'::jsonb::text;
SQL
)
SNAPSHOT_CHECKSUM=$(printf '%s' "$NORMALIZED_SNAPSHOT" | sha256sum | awk '{print $1}')

# 直接写入不可变已发布快照，仅用于验证 System 对账跨服务调用；不伪造业务 API 授权。
FAILURE_REASON="System reconciliation fixture could not be inserted"
docker exec -i "$POSTGRES_CONTAINER" psql \
  -U "$POSTGRES_USERNAME" -d "$POSTGRES_DATABASE" -v ON_ERROR_STOP=1 \
  -v "snapshot=${NORMALIZED_SNAPSHOT}" -v "checksum=${SNAPSHOT_CHECKSUM}" <<'SQL'
SET search_path TO mom_system;
INSERT INTO system_catalog_release (
    id, application_id, application_code, release_version,
    snapshot_schema_version, route_contract_version, source_application_version,
    source_release_version, snapshot_json, node_count, checksum, change_note,
    created_by, created_at, updated_by, updated_at)
VALUES (
    '2000000000000000001', '1000000000000000001', 'iam', 1,
    1, 1, 0, NULL, :'snapshot'::jsonb, 1, :'checksum', 'S18 client credentials smoke',
    's18-smoke', CURRENT_TIMESTAMP, 's18-smoke', CURRENT_TIMESTAMP);
INSERT INTO system_application (
    id, application_code, application_type, i18n_resource_code, i18n_message_key,
    icon_key, description, route_contract_version, sort_order, enabled,
    published_release_id, published_version, version,
    created_by, created_at, updated_by, updated_at)
VALUES (
    '1000000000000000001', 'iam', 'PLATFORM', 'mom-web', 'mom.menu.iam',
    NULL, 'S18 client credentials smoke', 1, 10, true,
    '2000000000000000001', 1, 0,
    's18-smoke', CURRENT_TIMESTAMP, 's18-smoke', CURRENT_TIMESTAMP);
SQL

POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$POSTGRES_PORT" \
POSTGRES_DATABASE="$POSTGRES_DATABASE" POSTGRES_SCHEMA=mom_system \
POSTGRES_USERNAME="$POSTGRES_USERNAME" POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
NACOS_DISCOVERY_ENABLED=false MANAGEMENT_HEALTH_REDIS_ENABLED=false \
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false MANAGEMENT_TRACING_EXPORT_OTLP_ENABLED=false \
MOM_RESOURCE_SERVER_ENABLED=false SYSTEM_RUNTIME_CACHE_ENABLED=false \
SYSTEM_RUNTIME_EVENT_CONSUMER_ENABLED=false OUTBOX_PUBLISHER_ENABLED=false \
IAM_PERMISSION_REFERENCE_URL="http://127.0.0.1:${IAM_PORT}" \
IAM_PERMISSION_REFERENCE_OAUTH2_ENABLED=true \
IAM_TOKEN_URI="http://127.0.0.1:${IAM_PORT}/oauth2/token" \
IAM_SYSTEM_CLIENT_ID="$CLIENT_ID" IAM_SYSTEM_CLIENT_SECRET="$CLIENT_SECRET" \
java -jar mom-system-platform/mom-system-server/target/mom-system-server-0.1.0-SNAPSHOT-exec.jar \
  --server.port="$SYSTEM_INTEGRATION_PORT" \
  --mom.system.catalog.permission-reconciliation.enabled=true \
  --mom.system.catalog.permission-reconciliation.run-on-startup=true \
  --mom.system.catalog.permission-reconciliation.initial-delay=PT1H \
  --mom.system.catalog.permission-reconciliation.interval=PT1H \
  > "$SYSTEM_LOG" 2>&1 &
SYSTEM_INTEGRATION_PID=$!
wait_readiness "$SYSTEM_INTEGRATION_PORT" "${KEY_DIR}/system-health.json" "System integration"

FAILURE_REASON="System startup IAM reconciliation did not complete"
grep --fixed-strings --quiet \
  "Catalog Reference 启动对账完成。applications=1 references=1 enabled=1 disabled=0 unknown=0" \
  "$SYSTEM_LOG" || exit 1

kill "$IAM_PID"
wait "$IAM_PID" 2>/dev/null || true
IAM_PID=""
sleep 5
FAILURE_REASON="System readiness failed after IAM outage"
status=$(curl --silent --output "${KEY_DIR}/system-after-iam-outage.json" \
  --write-out '%{http_code}' \
  "http://127.0.0.1:${SYSTEM_INTEGRATION_PORT}/actuator/health/readiness" || true)
[[ "$status" == "200" ]] && jq --exit-status '.status == "UP"' \
  "${KEY_DIR}/system-after-iam-outage.json" >/dev/null || {
  FAILURE_REASON="System readiness failed after IAM outage; HTTP ${status}"; exit 1;
}

{
  echo "SYSTEM_IAM_CLIENT_CREDENTIALS_SMOKE result=success token=issued direct_validation=success feign_reconciliation=success iam_outage_readiness=UP"
} >> system-postgresql-schema.txt
