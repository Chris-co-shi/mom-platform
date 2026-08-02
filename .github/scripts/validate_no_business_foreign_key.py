#!/usr/bin/env python3
"""阻断 MOM 正式业务 Migration 的物理外键与级联语法。"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
from dataclasses import dataclass, field

MIGRATION = re.compile(r"^mom-[^/]+-platform/mom-[^/]+-server/src/main/resources/db/migration/[^/]+/.+\.sql$")
CREATE_TABLE = re.compile(r"\bCREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+([a-z][a-z0-9_]*)", re.I)
FORBIDDEN = re.compile(r"\b(?:FOREIGN\s+KEY|REFERENCES|ON\s+DELETE\s+CASCADE|ON\s+UPDATE\s+CASCADE)\b", re.I)

# 已发布 Migration 的精确历史来源；它们不是新代码授权，阶段二通过更高版本 Migration 删除约束。
HISTORICAL_BUSINESS_SOURCES = {
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V1__create_iam_identity_tables.sql", "iam_internal_user_profile"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V1__create_iam_identity_tables.sql", "iam_external_user_binding"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_role"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_role_permission"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_application"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_factory_scope"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "iam_user_session"),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "iam_refresh_token"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V2__create_system_dictionary.sql", "system_dictionary_item"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V3__create_system_i18n.sql", "system_i18n_message"),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V3__create_system_i18n.sql", "system_i18n_release"),
}

# 历史来源只能由指定更高版本 Migration 精确闭环；约束名全部出现才视为已删除。
HISTORICAL_REMOVALS: dict[tuple[str, str], tuple[str, tuple[str, ...]]] = {
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V2__create_system_dictionary.sql", "system_dictionary_item"):
        ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V5__remove_business_foreign_keys.sql",
         ("fk_system_dictionary_item_dictionary",)),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V3__create_system_i18n.sql", "system_i18n_message"):
        ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V5__remove_business_foreign_keys.sql",
         ("fk_system_i18n_message_resource",)),
    ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V3__create_system_i18n.sql", "system_i18n_release"):
        ("mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V5__remove_business_foreign_keys.sql",
         ("fk_system_i18n_release_resource",)),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V1__create_iam_identity_tables.sql", "iam_internal_user_profile"):
        ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V9__remove_business_foreign_keys.sql",
         ("fk_iam_internal_profile_user",)),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V1__create_iam_identity_tables.sql", "iam_external_user_binding"):
        ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V9__remove_business_foreign_keys.sql",
         ("fk_iam_external_binding_user",)),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_role"):
        ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V9__remove_business_foreign_keys.sql",
         ("fk_iam_user_role_user", "fk_iam_user_role_role")),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_role_permission"):
        ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V9__remove_business_foreign_keys.sql",
         ("fk_iam_role_permission_role", "fk_iam_role_permission_permission")),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_application"):
        ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V9__remove_business_foreign_keys.sql",
         ("fk_iam_user_application_user",)),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V2__create_iam_rbac_scope_tables.sql", "iam_user_factory_scope"):
        ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V9__remove_business_foreign_keys.sql",
         ("fk_iam_user_factory_scope_user",)),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "iam_user_session"):
        ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V9__remove_business_foreign_keys.sql",
         ("fk_iam_user_session_user", "fk_iam_user_session_client_policy")),
    ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V3__create_iam_oauth_session_tables.sql", "iam_refresh_token"):
        ("mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V9__remove_business_foreign_keys.sql",
         ("fk_iam_refresh_token_session", "fk_iam_refresh_token_replacement")),
}

# 当前 SAS 官方表没有 FK；集合保留给精确协议表扩展，禁止目录和通配符。
OFFICIAL_PROTOCOL_EXCEPTIONS: set[tuple[str, str]] = set()


@dataclass
class Report:
    """保存阻断错误与历史来源提示。"""

    errors: list[str] = field(default_factory=list)
    reviews: list[str] = field(default_factory=list)


def strip_comments_and_literals(sql: str) -> str:
    """移除 SQL 注释、字符串与 dollar quote，避免示例文本误报。"""

    value = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S)
    value = re.sub(r"--[^\r\n]*", " ", value)
    value = re.sub(r"\$([A-Za-z_][A-Za-z0-9_]*)?\$.*?\$\1\$", " ", value, flags=re.S)
    return re.sub(r"'(?:''|[^'])*'", "''", value)


def create_table_statements(sql: str) -> list[tuple[str, str]]:
    """返回 CREATE TABLE 的精确表名与语句；Migration 不允许在表定义中使用动态标识符。"""

    cleaned = strip_comments_and_literals(sql)
    result: list[tuple[str, str]] = []
    for statement in cleaned.split(";"):
        match = CREATE_TABLE.search(statement)
        if match:
            result.append((match.group(1).lower(), statement))
    return result


def check_sql(path: str, sql: str, report: Report,
              protocol_exceptions: set[tuple[str, str]] | None = None,
              historical_sources: set[tuple[str, str]] | None = None,
              resolved_historical: set[tuple[str, str]] | None = None) -> None:
    """检查单个正式 Migration，只允许精确文件和表二元组。"""

    protocols = OFFICIAL_PROTOCOL_EXCEPTIONS if protocol_exceptions is None else protocol_exceptions
    historical = HISTORICAL_BUSINESS_SOURCES if historical_sources is None else historical_sources
    resolved = set() if resolved_historical is None else resolved_historical
    for table, statement in create_table_statements(sql):
        if not FORBIDDEN.search(statement):
            continue
        key = (path, table)
        if key in protocols:
            report.reviews.append(f"官方协议表精确例外: {path}#{table}")
        elif key in historical:
            if key in resolved:
                report.reviews.append(f"已发布业务 FK 历史来源，已由指定更高版本删除: {path}#{table}")
            else:
                report.errors.append(f"已发布业务 FK 历史来源尚未由指定更高版本完整删除: {path}#{table}")
        else:
            report.errors.append(f"正式业务 Migration 禁止物理外键或级联: {path}#{table}")


def git_files(root: pathlib.Path) -> list[str]:
    """读取跟踪和未跟踪文件，确保本地新增 Migration 也被检查。"""

    tracked = subprocess.check_output(["git", "ls-files"], cwd=root, text=True).splitlines()
    untracked = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=root, text=True).splitlines()
    return sorted(set(tracked) | set(untracked))


def run(root: pathlib.Path, report: Report) -> None:
    """扫描全部正式 Flyway SQL。"""

    files = git_files(root)
    resolved: set[tuple[str, str]] = set()
    for source, (removal_path, constraints) in HISTORICAL_REMOVALS.items():
        target = root / removal_path
        if not target.is_file():
            continue
        cleaned = strip_comments_and_literals(target.read_text(encoding="utf-8"))
        if all(re.search(rf"\bDROP\s+CONSTRAINT\s+{re.escape(name)}\b", cleaned, re.I)
               for name in constraints):
            resolved.add(source)
    for relative in files:
        if not MIGRATION.fullmatch(relative):
            continue
        path = root / relative
        if path.is_file():
            check_sql(relative, path.read_text(encoding="utf-8"), report,
                      resolved_historical=resolved)


def main(argv: list[str] | None = None) -> int:
    """命令行入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path.cwd())
    args = parser.parse_args(argv)
    report = Report()
    try:
        run(args.root.resolve(), report)
    except (OSError, subprocess.CalledProcessError) as exc:
        report.errors.append(f"无物理外键门禁执行失败: {exc}")
    if report.errors:
        print("NO_BUSINESS_FOREIGN_KEY_BASELINE: FAILED")
        for error in report.errors:
            print(f"- {error}")
        return 1
    print("NO_BUSINESS_FOREIGN_KEY_BASELINE: PASSED")
    print("- comments and string literals ignored; case-insensitive FK syntax parsed")
    print("- exceptions require exact Migration path and exact table")
    for review in report.reviews:
        print(f"- REVIEW: {review}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
