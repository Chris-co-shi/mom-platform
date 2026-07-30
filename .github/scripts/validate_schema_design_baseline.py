#!/usr/bin/env python3
"""MOM 新表 DDL 的可静态证明规则与精确 Review Candidate 门禁。"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
from dataclasses import dataclass, field

MIGRATION = re.compile(
    r"^mom-(?P<service>[a-z0-9-]+)-platform/mom-[^/]+-server/src/main/resources/db/migration/"
    r"(?P<context>[a-z0-9-]+)/(?P<name>.+\.sql)$"
)
CREATE_TABLE = re.compile(r"\bCREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([A-Za-z_][A-Za-z0-9_]*)", re.I)
SNAKE = re.compile(r"^[a-z][a-z0-9_]*$")
COLUMN = re.compile(r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s+([^,]+)", re.I)
SCHEMA_REF = re.compile(r"\bmom_([a-z][a-z0-9_]*)\s*\.", re.I)
KNOWN_CONTEXTS = {"iam", "mdm", "mes", "wms", "qms", "ems", "eam", "integration", "traceability", "system"}

# 已发布表的精确历史基线；只跳过既有 CREATE TABLE，不授权新增表复制旧设计。
LEGACY_TABLES = {
    ("mom-integration-platform/mom-integration-server/src/main/resources/db/migration/integration/V1__create_inbox_and_message_receipt.sql", "mom_inbox_event"),
    ("mom-integration-platform/mom-integration-server/src/main/resources/db/migration/integration/V1__create_inbox_and_message_receipt.sql", "technical_message_receipt"),
    ("mom-integration-platform/mom-integration-server/src/main/resources/db/migration/integration/V2__create_seata_at_probe.sql", "undo_log"),
    ("mom-integration-platform/mom-integration-server/src/main/resources/db/migration/integration/V2__create_seata_at_probe.sql", "technical_seata_at_participant"),
    ("mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V1__create_technical_data_probe.sql", "technical_data_probe"),
    ("mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V3__create_outbox_event.sql", "mom_outbox_event"),
    ("mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V4__create_seata_at_probe.sql", "undo_log"),
    ("mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V4__create_seata_at_probe.sql", "technical_seata_at_coordinator"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V1__create_iam_identity_tables.sql", "iam_user"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V1__create_iam_identity_tables.sql", "iam_internal_user_profile"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V1__create_iam_identity_tables.sql", "iam_external_user_binding"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_role"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_permission"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_role"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_role_permission"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_application"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_factory_scope"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_oauth_client_policy"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "oauth2_registered_client"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "oauth2_authorization"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "oauth2_authorization_consent"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "iam_user_session"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "iam_refresh_token"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V4__create_iam_security_audit_tables.sql", "iam_security_audit_event"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V1__create_system_parameter.sql", "system_parameter"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V2__create_system_dictionary.sql", "system_dictionary"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V2__create_system_dictionary.sql", "system_dictionary_item"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V3__create_system_i18n.sql", "system_i18n_resource"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V3__create_system_i18n.sql", "system_i18n_message"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V3__create_system_i18n.sql", "system_i18n_release"),
}


@dataclass
class Report:
    """保存阻断错误与无法静态证明的候选。"""

    errors: list[str] = field(default_factory=list)
    reviews: list[str] = field(default_factory=list)


def clean(sql: str) -> str:
    """删除注释和字符串内容，保留 DDL 结构。"""

    value = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S)
    value = re.sub(r"--[^\r\n]*", " ", value)
    value = re.sub(r"\$([A-Za-z_][A-Za-z0-9_]*)?\$.*?\$\1\$", " ", value, flags=re.S)
    return re.sub(r"'(?:''|[^'])*'", "''", value)


def table_statements(sql: str) -> list[tuple[str, str]]:
    """提取 CREATE TABLE 语句。"""

    result = []
    for statement in clean(sql).split(";"):
        match = CREATE_TABLE.search(statement)
        if match:
            result.append((match.group(1), statement))
    return result


def check_table(path: str, table: str, statement: str, full_sql: str, report: Report,
                legacy_tables: set[tuple[str, str]] | None = None) -> None:
    """校验一张新表的命名、类型、约束和注释。"""

    legacy = LEGACY_TABLES if legacy_tables is None else legacy_tables
    table_lower = table.lower()
    if (path, table_lower) in legacy:
        report.reviews.append(f"已发布表结构历史基线，新增实现不得复制: {path}#{table_lower}")
        return
    if table != table_lower or not SNAKE.fullmatch(table):
        report.errors.append(f"表名必须小写 snake_case: {path}#{table}")

    columns: dict[str, str] = {}
    body = statement[statement.find("(") + 1: statement.rfind(")")]
    for raw in body.splitlines():
        match = COLUMN.match(raw)
        if not match:
            continue
        name, definition = match.group(1), match.group(2)
        if name.upper() in {"CONSTRAINT", "PRIMARY", "UNIQUE", "CHECK", "FOREIGN"}:
            continue
        if name != name.lower() or not SNAKE.fullmatch(name):
            report.errors.append(f"列名必须小写 snake_case: {path}#{table_lower}.{name}")
        columns[name.lower()] = definition

    if "id" in columns and not re.search(r"\bvarchar\s*\(\s*19\s*\)", columns["id"], re.I):
        report.errors.append(f"MOM 技术主键必须为 varchar(19): {path}#{table_lower}.id")
    if re.search(r"\btimestamp\s*(?:without\s+time\s+zone)?\b", statement, re.I) and not re.search(r"\btimestamptz\b", statement, re.I):
        report.errors.append(f"技术时间点禁止无时区 timestamp: {path}#{table_lower}")
    if re.search(r"\b(?:float\d*|real|double\s+precision)\b", statement, re.I):
        report.errors.append(f"精度敏感字段禁止浮点数据库类型: {path}#{table_lower}")

    required_constraints = (("PRIMARY KEY", f"pk_{table_lower}"), ("UNIQUE", f"uk_{table_lower}_"), ("CHECK", f"ck_{table_lower}_"))
    for keyword, prefix in required_constraints:
        if re.search(rf"\b{keyword}\b", statement, re.I) and not re.search(rf"\bCONSTRAINT\s+{re.escape(prefix)}[a-z0-9_]*", statement, re.I):
            report.errors.append(f"{keyword} 必须显式使用 {prefix}<semantic>: {path}#{table_lower}")
    if "version" in columns and not re.search(
        rf"\bCONSTRAINT\s+ck_{re.escape(table_lower)}_[a-z0-9_]*\s+CHECK\s*\([^)]*version\s*>=\s*0", statement, re.I | re.S
    ):
        report.errors.append(f"Version 必须有非负命名 Check: {path}#{table_lower}.version")

    if not re.search(rf"\bCOMMENT\s+ON\s+TABLE\s+{re.escape(table_lower)}\s+IS\b", full_sql, re.I):
        report.errors.append(f"正式业务表必须有表注释: {path}#{table_lower}")
    for column in sorted(name for name in columns if name == "id" or name.endswith("_id") or name in {"status", "state", "version"}):
        if not re.search(rf"\bCOMMENT\s+ON\s+COLUMN\s+{re.escape(table_lower)}\.{re.escape(column)}\s+IS\b", full_sql, re.I):
            report.errors.append(f"关键列必须有注释: {path}#{table_lower}.{column}")

    report.reviews.append(f"字段长度、索引有效性、生命周期与业务完整性需 Review: {path}#{table_lower}")


def check_sql(path: str, sql: str, report: Report, legacy_tables: set[tuple[str, str]] | None = None) -> None:
    """检查单个 Migration 的表、索引和跨 Schema 引用。"""

    match = MIGRATION.fullmatch(path)
    if not match:
        return
    context = match.group("context").replace("-", "_")
    cleaned = clean(sql)
    for target in sorted({
        item.group(1).lower() for item in SCHEMA_REF.finditer(cleaned)
        if item.group(1).lower() in KNOWN_CONTEXTS
    }):
        if target != context:
            report.errors.append(f"Migration 禁止跨 Schema: {path} -> mom_{target}")
    for table, statement in table_statements(sql):
        check_table(path, table, statement, sql, report, legacy_tables)
    legacy = LEGACY_TABLES if legacy_tables is None else legacy_tables
    created_tables = {table.lower() for table, _ in table_statements(sql)}
    if any((path, table) in legacy for table in created_tables):
        return
    for index in re.finditer(r"\bCREATE\s+(UNIQUE\s+)?INDEX(?:\s+CONCURRENTLY)?\s+([A-Za-z_][A-Za-z0-9_]*)", cleaned, re.I):
        unique, name = bool(index.group(1)), index.group(2)
        prefix = "uk_" if unique else "ix_"
        if name != name.lower() or not name.lower().startswith(prefix):
            report.errors.append(f"索引必须显式使用 {prefix}<table>_<semantic>: {path}#{name}")


def git_files(root: pathlib.Path) -> list[str]:
    """读取当前工作树文件。"""

    tracked = subprocess.check_output(["git", "ls-files"], cwd=root, text=True).splitlines()
    untracked = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=root, text=True).splitlines()
    return sorted(set(tracked) | set(untracked))


def run(root: pathlib.Path, report: Report) -> None:
    """扫描正式 Migration；版本不可变性由 Persistence Baseline 共同证明。"""

    for relative in git_files(root):
        if not MIGRATION.fullmatch(relative):
            continue
        path = root / relative
        if path.is_file():
            check_sql(relative, path.read_text(encoding="utf-8"), report)


def main(argv: list[str] | None = None) -> int:
    """命令行入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path.cwd())
    args = parser.parse_args(argv)
    report = Report()
    try:
        run(args.root.resolve(), report)
    except (OSError, subprocess.CalledProcessError) as exc:
        report.errors.append(f"表结构门禁执行失败: {exc}")
    if report.errors:
        print("SCHEMA_DESIGN_BASELINE: FAILED")
        for error in report.errors:
            print(f"- {error}")
        return 1
    print("SCHEMA_DESIGN_BASELINE: PASSED")
    print("- new tables use snake_case, named constraints/indexes, safe ID/time/decimal types and comments")
    print("- published Versioned Migration immutability is jointly enforced by Persistence Baseline")
    for review in report.reviews:
        print(f"- REVIEW: {review}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
