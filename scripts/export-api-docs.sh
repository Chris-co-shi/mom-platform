#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
snapshot_dir="$repo_root/docs/api/snapshots"
mode="${1:---check}"

if [[ "$mode" != "--check" && "$mode" != "--update" ]]; then
  printf '用法: bash scripts/export-api-docs.sh [--check|--update]\n' >&2
  exit 2
fi

for command in curl jq; do
  command -v "$command" >/dev/null 2>&1 || { printf '缺少命令: %s\n' "$command" >&2; exit 2; }
done

mkdir -p "$snapshot_dir"

export_one() {
  local owner="$1" url="$2" title="$3" protected_path="$4"
  local snapshot="$snapshot_dir/$owner-openapi.json"
  local raw normalized
  local base_url="${url%/v3/api-docs}"
  local protected_status
  raw="$(mktemp)"
  normalized="$(mktemp)"

  if ! curl --fail --silent --show-error --max-time 20 "$url" -o "$raw"; then
    rm -f "$raw" "$normalized"
    printf '文档端点不可用: %s\n' "$url" >&2
    return 1
  fi

  if ! jq --sort-keys --arg title "$title" \
      'if .openapi == "3.1.0" and .info.title == $title and (.paths | length > 0)
       then del(.servers) else error("OpenAPI 版本、服务名称或 paths 不正确") end' \
      "$raw" > "$normalized"; then
    rm -f "$raw" "$normalized"
    printf '文档内容校验失败: %s\n' "$url" >&2
    return 1
  fi

  protected_status="$(curl --silent --show-error --max-time 10 --output /dev/null \
    --write-out '%{http_code}' "$base_url$protected_path")"
  if [[ "$protected_status" != "401" ]]; then
    rm -f "$raw" "$normalized"
    printf '匿名业务接口未按预期返回 401: %s (HTTP %s)\n' "$base_url$protected_path" "$protected_status" >&2
    return 1
  fi

  if [[ "$mode" == "--check" ]]; then
    if ! cmp -s "$snapshot" "$normalized"; then
      rm -f "$raw" "$normalized"
      printf 'OpenAPI 快照存在差异: %s\n' "$snapshot" >&2
      return 1
    fi
    printf 'OpenAPI 快照一致: %s\n' "$owner"
    rm -f "$raw" "$normalized"
  else
    mv "$normalized" "$snapshot"
    rm -f "$raw"
    printf '已更新 OpenAPI 快照: %s\n' "$snapshot"
  fi
}

export_one auth "${MOM_AUTH_DOC_URL:-http://127.0.0.1:20001/v3/api-docs}" 'MOM Auth Service API' '/me'
export_one system "${MOM_SYSTEM_DOC_URL:-http://127.0.0.1:20300/v3/api-docs}" 'MOM System Service API' '/admin/dictionaries'
export_one mdm "${MOM_MDM_DOC_URL:-http://127.0.0.1:20200/v3/api-docs}" 'MOM MDM Service API' '/api/mdm/materials'
