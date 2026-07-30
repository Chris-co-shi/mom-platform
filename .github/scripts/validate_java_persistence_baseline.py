#!/usr/bin/env python3
"""MOM 正式 bounded context 的 JDBC 技术栈与 Java SQL 轻量门禁。"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
from dataclasses import dataclass, field

SERVER_MAIN_JAVA = re.compile(r"^mom-[^/]+-platform/mom-[^/]+-server/src/main/java/.+\.java$")
DIRECT_JDBC_IMPORT = re.compile(r"^\s*import\s+(?:org\.springframework\.jdbc\.|java\.sql\.)", re.MULTILINE)
SQL_ADAPTER_IMPORT = re.compile(
    r"^\s*import\s+(?:org\.apache\.ibatis\.annotations\.(?:Select|Insert|Update|Delete)|"
    r"org\.springframework\.jdbc\.)",
    re.MULTILINE,
)
SELECT_STAR = re.compile(r"\bSELECT\s+\*\s+FROM\b", re.IGNORECASE | re.DOTALL)
DYNAMIC_SQL_TEXT = re.compile(r"\$\{")

DIRECT_JDBC_EXCEPTIONS = {
    "mom-iam-platform/mom-iam-server/src/main/java/"
    "io/github/chrisshi/mom/iam/security/IamAuthorizationServerProtocolConfiguration.java",
    "mom-mdm-platform/mom-mdm-server/src/main/java/"
    "io/github/chrisshi/mom/mdm/application/MdmSeataAtLocalParticipantService.java",
    "mom-integration-platform/mom-integration-server/src/main/java/"
    "io/github/chrisshi/mom/integration/application/IntegrationSeataAtParticipantService.java",
    "mom-integration-platform/mom-integration-server/src/main/java/"
    "io/github/chrisshi/mom/integration/messaging/IntegrationDomainEventConsumerConfiguration.java",
}

# S02 已登记且尚未完成迁移的两个精确历史注解 SQL；不得增加新文件名。
SELECT_STAR_EXCEPTION_NAMES = {
    "IamExternalUserBindingMapper.java",
    "IamUserSessionMapper.java",
}


@dataclass
class Report:
    """保存阻断错误。"""

    errors: list[str] = field(default_factory=list)


def check_java_file(relative: str, text: str, report: Report) -> None:
    """检查单个正式 Server Java 文件的技术栈和可证明 SQL 风险。"""

    if not SERVER_MAIN_JAVA.fullmatch(relative):
        return

    if DIRECT_JDBC_IMPORT.search(text) and relative not in DIRECT_JDBC_EXCEPTIONS:
        report.errors.append(f"正式 bounded context 禁止直接 JDBC: {relative}")

    if not SQL_ADAPTER_IMPORT.search(text):
        return

    if SELECT_STAR.search(text) and pathlib.PurePosixPath(relative).name not in SELECT_STAR_EXCEPTION_NAMES:
        report.errors.append(f"正式 Java SQL 必须显式列名，禁止 SELECT *: {relative}")
    if DYNAMIC_SQL_TEXT.search(text):
        report.errors.append(f"Java SQL 禁止 ${{}} 动态文本: {relative}")


def git_lines(root: pathlib.Path, *args: str) -> list[str]:
    """读取 Git 文件列表。"""

    output = subprocess.check_output(["git", *args], cwd=root, text=True)
    return [line for line in output.splitlines() if line]


def run(root: pathlib.Path, report: Report) -> None:
    """扫描当前工作树中的正式 Server Java 文件。"""

    tracked = set(git_lines(root, "ls-files"))
    untracked = set(git_lines(root, "ls-files", "--others", "--exclude-standard"))
    for relative in sorted(tracked | untracked):
        if not SERVER_MAIN_JAVA.fullmatch(relative):
            continue
        path = root / relative
        if path.is_file():
            check_java_file(relative, path.read_text(encoding="utf-8"), report)


def main(argv: list[str] | None = None) -> int:
    """命令行入口；兼容持久化门禁既有 base/head 参数。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--base")
    parser.add_argument("--head", default="HEAD")
    args = parser.parse_args(argv)
    report = Report()
    try:
        run(args.root.resolve(), report)
    except (OSError, subprocess.CalledProcessError) as exc:
        report.errors.append(f"Java 持久化门禁执行失败: {exc}")
    if report.errors:
        print("JAVA_PERSISTENCE_BASELINE: FAILED")
        for error in report.errors:
            print(f"- {error}")
        return 1
    print("JAVA_PERSISTENCE_BASELINE: PASSED")
    print("- bounded contexts use MyBatis/MyBatis-Plus unless precisely exempted")
    print("- Java annotation/JDBC SQL rejects SELECT * and ${} dynamic text")
    return 0


if __name__ == "__main__":
    sys.exit(main())
