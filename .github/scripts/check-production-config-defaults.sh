#!/usr/bin/env bash
set -Eeuo pipefail

repository_root=$(git rev-parse --show-toplevel)
failure_count=0

report_failure() {
  local rule="$1"
  local file="$2"
  printf '生产配置安全门禁失败：%s（%s）\n' "$rule" "${file#"$repository_root/"}" >&2
  failure_count=$((failure_count + 1))
}

while IFS= read -r -d '' config_file; do
  if grep -Eq '\$\{[A-Z0-9_]*(PASSWORD|SECRET|TOKEN):[^}]+\}' "$config_file"; then
    report_failure "敏感环境变量包含非空默认值" "$config_file"
  fi

  if grep -Eq '(^|[^0-9])(10\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}|192\.168\.[0-9]{1,3}\.[0-9]{1,3}|172\.(1[6-9]|2[0-9]|3[01])\.[0-9]{1,3}\.[0-9]{1,3}|100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.[0-9]{1,3}\.[0-9]{1,3})([^0-9]|$)' "$config_file"; then
    report_failure "生产默认值包含私有或 CGNAT 远程地址" "$config_file"
  fi

  if grep -Eq '\$\{(IAM_BOOTSTRAP_ENABLED|NACOS_DISCOVERY_ENABLED|OTEL_METRICS_EXPORT_ENABLED|OTEL_TRACING_EXPORT_ENABLED):true\}' "$config_file"; then
    report_failure "外部接入或高风险能力默认启用" "$config_file"
  fi

  if grep -Eq '^[[:space:]]*(password|secret|private-key):[[:space:]]+[^$[:space:]][^[:space:]]*' "$config_file"; then
    report_failure "生产配置包含字面量敏感值" "$config_file"
  fi
done < <(find "$repository_root" \
  -path '*/target' -prune -o \
  -path '*/src/test/*' -prune -o \
  -path '*/src/main/resources/application*.yml' -type f -print0)

if (( failure_count > 0 )); then
  printf '生产配置安全门禁共发现 %d 个问题；输出已省略秘密值。\n' "$failure_count" >&2
  exit 1
fi

printf '生产配置安全默认值检查通过。\n'
