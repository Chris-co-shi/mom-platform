#!/usr/bin/env bash
set -Eeuo pipefail

# 同一组生产 JAR 依次使用两种真实数据库启动。临时库与账号仅用于本次检查。
docker info >/dev/null
run_id="mom-database-smoke-$$"
pg="${run_id}-pg"
mysql="${run_id}-mysql"
mkdir -p .codex/runtime/logs
scratch="$(mktemp -d .codex/runtime/logs/database-vendors-smoke.XXXXXX)"
password="mom-smoke-$$"
app_pid=
# 数据库冒烟沿用现有 MDM 启动检查的本地 HTTP 探针配置。
security_exclusions="org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration"
security_exclusions+=",org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
security_exclusions+=",org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration"
security_exclusions+=",org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration"
security_exclusions+=",org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"

cleanup() {
  local status=$?
  if [[ -n "$app_pid" ]]; then kill "$app_pid" 2>/dev/null || true; fi
  docker rm -fv "$pg" "$mysql" >/dev/null 2>&1 || true
  if [[ "$status" -eq 0 ]]; then rm -rf "$scratch"; else
    printf '数据库冒烟日志目录 %s\n' "$scratch" >&2
  fi
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

docker run -d --name "$pg" -p 127.0.0.1::5432 \
  -e POSTGRES_PASSWORD="$password" -e POSTGRES_DB=mom_platform postgres:17.7-alpine >/dev/null
docker run -d --name "$mysql" -p 127.0.0.1::3306 \
  -e MYSQL_ROOT_PASSWORD="$password" mysql:8.4 --log-bin-trust-function-creators=ON >/dev/null

await_command() {
  for ((attempt=0; attempt<90; attempt++)); do
    if "$@" >/dev/null 2>&1; then return; fi
    sleep 1
  done
  printf '数据库在期限内未就绪\n' >&2
  return 1
}
await_command docker exec "$pg" pg_isready -h 127.0.0.1 -U postgres -d mom_platform
await_command docker exec -e MYSQL_PWD="$password" "$mysql" mysql -h 127.0.0.1 -uroot -e 'SELECT 1'
pg_port="$(docker port "$pg" 5432/tcp | cut -d: -f2)"
mysql_port="$(docker port "$mysql" 3306/tcp | cut -d: -f2)"

for owner in auth system mdm qms wms integration; do
  docker exec -i -e MYSQL_PWD="$password" "$mysql" mysql -uroot <<SQL
CREATE DATABASE mom_${owner} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE USER 'mom_${owner}'@'%' IDENTIFIED BY '${password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, TRIGGER ON mom_${owner}.* TO 'mom_${owner}'@'%';
SQL
done

start_service() {
  local owner="$1" vendor="$2" attempt="$3" http_port= driver_log actuator=false
  if [[ "$vendor" == mysql ]]; then
    driver_log='Added connection com.mysql.cj.jdbc.ConnectionImpl'
  else
    driver_log='Added connection org.postgresql.jdbc.PgConnection'
  fi
  local jars=("mom-${owner}-platform/mom-${owner}-server/target/"*-exec.jar)
  [[ "${#jars[@]}" -eq 1 && -f "${jars[0]}" ]]
  if unzip -Z1 "${jars[0]}" | grep -E '^BOOT-INF/lib/spring-boot-health-' >/dev/null; then
    actuator=true
  fi
  local log="$scratch/$owner-$vendor-$attempt.log"
  MOM_DATABASE_VENDOR="$vendor" \
    POSTGRES_URL="jdbc:postgresql://127.0.0.1:$pg_port/mom_platform?currentSchema=mom_$owner" \
    POSTGRES_USERNAME=postgres POSTGRES_PASSWORD="$password" POSTGRES_SCHEMA="mom_$owner" \
    MYSQL_URL="jdbc:mysql://127.0.0.1:$mysql_port/mom_$owner" \
    MYSQL_DATABASE="mom_$owner" MYSQL_USERNAME="mom_$owner" MYSQL_PASSWORD="$password" \
    java -Duser.timezone=America/Los_Angeles -jar "${jars[0]}" --server.port=0 \
      --spring.datasource.username="$([[ "$vendor" == mysql ]] && printf 'mom_%s' "$owner" || printf postgres)" \
      --spring.cloud.nacos.discovery.enabled=false --management.health.redis.enabled=false \
      --management.endpoint.health.probes.enabled=true --mom.security.resource-server.enabled=false \
      --spring.autoconfigure.exclude="$security_exclusions" >"$log" 2>&1 &
  app_pid=$!
  for ((poll=0; poll<90; poll++)); do
    if ! kill -0 "$app_pid" 2>/dev/null; then break; fi
    http_port="$(sed -nE 's/.*Tomcat started on port ([0-9]+).*/\1/p' "$log" | tail -1)"
    if [[ -n "$http_port" ]] && grep -Eq 'Started [^ ]+Application in ' "$log"; then
      if [[ "$actuator" == true ]] && ! curl -fsS --max-time 2 "http://127.0.0.1:$http_port/actuator/health/readiness" \
          | jq -e '.status == "UP"' >/dev/null; then
        sleep 1
        continue
      fi
      [[ "$(grep -c 'HikariDataSource .*Start completed' "$log")" -eq 1 ]]
      grep -Fq "$driver_log" "$log"
      if [[ "$attempt" == restart ]]; then
        grep -Fq 'No migration necessary.' "$log"
      fi
      kill "$app_pid"
      wait "$app_pid" 2>/dev/null || true
      app_pid=
      printf '%s %s %s READY\n' "$owner" "$vendor" "$attempt"
      return
    fi
    sleep 1
  done
  printf '%s %s %s 未就绪，日志 %s\n' "$owner" "$vendor" "$attempt" "$log" >&2
  return 1
}

for owner in auth system mdm qms wms integration; do
  start_service "$owner" postgresql init
  start_service "$owner" mysql init
  start_service "$owner" mysql restart
done
