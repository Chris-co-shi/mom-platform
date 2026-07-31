#!/usr/bin/env python3
"""MOM 正式 bounded context 的 JDBC、Java SQL 与 Repository 抽象门禁。"""

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
MP_SERVICE_IMPORT = re.compile(
    r"^\s*import\s+com\.baomidou\.mybatisplus\.extension\.service\.", re.MULTILINE
)
MP_REPOSITORY_IMPORT = re.compile(
    r"^\s*import\s+com\.baomidou\.mybatisplus\.extension\.repository\.", re.MULTILINE
)
CRUD_REPOSITORY_EXTENDS = re.compile(r"\bextends\s+CrudRepository\s*<")
EXPLICIT_IREPOSITORY_CONTRACT = re.compile(
    r"\b(?:extends|implements)\s+[^\{;]*\bIRepository\s*<", re.MULTILINE
)
MOM_ADAPTER_IMPORT = re.compile(
    r"^\s*import\s+io\.github\.chrisshi\.mom\.[^.]+\.infrastructure\.persistence\.repository\."
    r"Mybatis[A-Za-z0-9_]*Repository\s*;",
    re.MULTILINE,
)
REPOSITORY_IMPLEMENTATION = re.compile(
    r"\bimplements\s+(?!IRepository\b)[A-Za-z0-9_$.]*Repository\b"
)
INFRA_REPOSITORY_SEGMENT = "/infrastructure/persistence/repository/"
UPPER_LAYER_SEGMENTS = ("/domain/", "/application/", "/web/", "/interfaces/")

REQUIRED_CRUD_REPOSITORIES = {
    "mom-system-platform/mom-system-server/src/main/java/"
    "io/github/chrisshi/mom/system/infrastructure/persistence/repository/"
    "MybatisSystemParameterRepository.java",
    "mom-system-platform/mom-system-server/src/main/java/"
    "io/github/chrisshi/mom/system/infrastructure/persistence/repository/"
    "MybatisSystemDictionaryRepository.java",
    "mom-system-platform/mom-system-server/src/main/java/"
    "io/github/chrisshi/mom/system/infrastructure/persistence/repository/"
    "MybatisSystemDictionaryItemRepository.java",
}

DIRECT_JDBC_EXCEPTIONS = {
    "mom-iam-platform/mom-iam-server/src/main/java/"
    "io/github/chrisshi/mom/iam/security/IamAuthorizationServerProtocolConfiguration.java",
}


@dataclass
class Report:
    """保存阻断错误。"""

    errors: list[str] = field(default_factory=list)


def check_repository_abstraction(relative: str, text: str, report: Report) -> None:
    """检查 MyBatis-Plus Repository 复用位置和领域边界。"""

    if MP_SERVICE_IMPORT.search(text):
        report.errors.append(f"正式 bounded context 禁止 IService/ServiceImpl: {relative}")

    uses_mp_repository = bool(MP_REPOSITORY_IMPORT.search(text) or CRUD_REPOSITORY_EXTENDS.search(text))
    if uses_mp_repository and INFRA_REPOSITORY_SEGMENT not in relative:
        report.errors.append(f"MyBatis-Plus Repository 只能位于 Infrastructure Repository: {relative}")

    if EXPLICIT_IREPOSITORY_CONTRACT.search(text):
        report.errors.append(f"MOM 契约不得显式继承或实现 IRepository: {relative}")

    if CRUD_REPOSITORY_EXTENDS.search(text):
        if not REPOSITORY_IMPLEMENTATION.search(text):
            report.errors.append(f"CrudRepository Adapter 必须实现 MOM Repository Port: {relative}")
        if not pathlib.PurePosixPath(relative).name.startswith("Mybatis"):
            report.errors.append(f"CrudRepository Adapter 必须使用 Mybatis*Repository 命名: {relative}")

    if relative in REQUIRED_CRUD_REPOSITORIES and not CRUD_REPOSITORY_EXTENDS.search(text):
        report.errors.append(f"单表 Domain Repository Adapter 必须复用 CrudRepository: {relative}")

    if any(segment in relative for segment in UPPER_LAYER_SEGMENTS) and MOM_ADAPTER_IMPORT.search(text):
        report.errors.append(f"Domain/Application/Web 不得依赖具体 MyBatis Repository Adapter: {relative}")


def check_java_file(relative: str, text: str, report: Report) -> None:
    """检查单个正式 Server Java 文件的技术栈、SQL 和 Repository 抽象。"""

    if not SERVER_MAIN_JAVA.fullmatch(relative):
        return

    if DIRECT_JDBC_IMPORT.search(text) and relative not in DIRECT_JDBC_EXCEPTIONS:
        report.errors.append(f"正式 bounded context 禁止直接 JDBC: {relative}")

    check_repository_abstraction(relative, text, report)

    if not SQL_ADAPTER_IMPORT.search(text):
        return

    if SELECT_STAR.search(text):
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
    print("- CrudRepository stays inside Infrastructure and implements a MOM Repository Port")
    print("- IService/ServiceImpl and explicit IRepository contracts remain forbidden")
    print("- Java annotation/JDBC SQL rejects SELECT * and ${} dynamic text")
    return 0


if __name__ == "__main__":
    sys.exit(main())
