#!/usr/bin/env python3
"""MOM Flyway 不可变性、物理 Schema 与 Mapper XML 的轻量持久化门禁。"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

MIGRATION_PATH = re.compile(
    r"^(?P<prefix>.+/)?mom-(?P<service>[a-z0-9-]+)-server/"
    r"src/main/resources/db/migration/(?P<context>[a-z0-9-]+)/(?P<name>[^/]+)$"
)
VERSIONED_NAME = re.compile(r"^V(?P<version>[0-9]+(?:[._][0-9]+)*)__[a-z0-9][a-z0-9_]*\.sql$")
REPEATABLE_NAME = re.compile(r"^R__[a-z0-9][a-z0-9_]*\.sql$")
MOM_SCHEMA = re.compile(r"\bmom_([a-z][a-z0-9_]*)\s*\.", re.IGNORECASE)
KNOWN_CONTEXTS = {
    "iam", "mdm", "mes", "wms", "qms", "ems", "eam", "integration", "traceability", "system"
}
XML_SQL_TAGS = {"select", "insert", "update", "delete"}


@dataclass
class Report:
    """保存阻断错误和需人工复核的精确候选。"""

    errors: list[str] = field(default_factory=list)
    reviews: list[str] = field(default_factory=list)


def strip_sql_literals_and_comments(sql: str) -> str:
    """移除注释与字符串内容，避免把示例、参数值或注释误判为 Schema 引用。"""

    without_block = re.sub(r"/\*.*?\*/", " ", sql, flags=re.DOTALL)
    without_line = re.sub(r"--[^\r\n]*", " ", without_block)
    without_dollar = re.sub(r"\$([A-Za-z_][A-Za-z0-9_]*)?\$.*?\$\1\$", " ", without_line, flags=re.DOTALL)
    return re.sub(r"'(?:''|[^'])*'", " ", without_dollar)


def migration_details(relative: str) -> tuple[str, str, str] | None:
    """返回生产 Migration 的服务、上下文和文件名；测试资源明确排除。"""

    match = MIGRATION_PATH.match(relative)
    if not match:
        return None
    return match.group("service"), match.group("context"), match.group("name")


def check_migration_paths(files: list[str], contents: dict[str, str], report: Report) -> None:
    """校验命名、路径版本唯一性和跨服务物理 Schema 引用。"""

    versions: dict[tuple[str, str], str] = {}
    for relative in sorted(files):
        details = migration_details(relative)
        if details is None:
            continue
        service, context, name = details
        if service != context:
            report.errors.append(f"Migration 路径与服务不匹配: {relative}")
        version_match = VERSIONED_NAME.fullmatch(name)
        if not version_match and not REPEATABLE_NAME.fullmatch(name):
            report.errors.append(f"Migration 命名非法: {relative}")
        if version_match:
            normalized = version_match.group("version").replace("_", ".")
            key = (relative.rsplit("/", 1)[0], normalized)
            previous = versions.get(key)
            if previous:
                report.errors.append(f"Migration 版本重复: {previous} 与 {relative}")
            versions[key] = relative

        cleaned = strip_sql_literals_and_comments(contents.get(relative, ""))
        for target in sorted({
            match.group(1).lower() for match in MOM_SCHEMA.finditer(cleaned)
            if match.group(1).lower() in KNOWN_CONTEXTS
        }):
            if target != context.replace("-", "_"):
                report.errors.append(f"Migration 引用其他 MOM Schema: {relative} -> mom_{target}")


def check_versioned_immutability(changes: list[tuple[str, ...]], base_files: set[str], report: Report) -> None:
    """使用 Base/Head Git 变更识别已存在 Versioned Migration 的修改、删除或重命名。"""

    base_versions: dict[str, list[tuple[int, ...]]] = {}
    for base_path in base_files:
        details = migration_details(base_path)
        if details is None:
            continue
        match = VERSIONED_NAME.fullmatch(details[2])
        if match:
            directory = base_path.rsplit("/", 1)[0]
            base_versions.setdefault(directory, []).append(version_tuple(match.group("version")))

    for change in changes:
        status = change[0]
        old_path = change[1]
        if status == "A":
            details = migration_details(old_path)
            match = VERSIONED_NAME.fullmatch(details[2]) if details else None
            if match:
                directory = old_path.rsplit("/", 1)[0]
                existing = base_versions.get(directory, [])
                if existing and version_tuple(match.group("version")) <= max(existing):
                    report.errors.append(f"新增 Versioned Migration 必须高于 Base 最大版本: {old_path}")
            continue
        if old_path not in base_files or migration_details(old_path) is None:
            continue
        name = old_path.rsplit("/", 1)[-1]
        if not VERSIONED_NAME.fullmatch(name):
            continue
        if status.startswith(("M", "D", "R", "C")):
            report.errors.append(f"已存在 Versioned Migration 不得{status[0]}: {old_path}")


def version_tuple(version: str) -> tuple[int, ...]:
    """按 Flyway 数字段落比较项目允许的点号/下划线版本。"""

    return tuple(int(part) for part in re.split(r"[._]", version))


def check_mapper_xml(path: str, xml_text: str, report: Report) -> None:
    """解析 MyBatis XML，报告动态文本、跨 Schema 和显然无条件写入。"""

    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as exc:
        report.errors.append(f"Mapper XML 无法解析: {path}: {exc}")
        return
    for element in root.iter():
        tag = element.tag.rsplit("}", 1)[-1]
        if tag not in XML_SQL_TAGS:
            continue
        statement_id = element.attrib.get("id", "<missing-id>")
        sql = " ".join(element.itertext())
        cleaned = strip_sql_literals_and_comments(sql)
        if "${" in cleaned:
            report.errors.append(f"Mapper 动态文本候选: {path}#{statement_id}")
        for target in sorted({
            match.group(1).lower() for match in MOM_SCHEMA.finditer(cleaned)
            if match.group(1).lower() in KNOWN_CONTEXTS
        }):
            report.errors.append(f"Mapper 引用限定 MOM Schema: {path}#{statement_id} -> mom_{target}")
        if tag in {"update", "delete"}:
            child_tags = {child.tag.rsplit("}", 1)[-1] for child in element.iter()}
            has_condition = re.search(r"\bwhere\b", cleaned, re.IGNORECASE) or "where" in child_tags
            has_include = "include" in child_tags
            if not has_condition and not has_include:
                report.errors.append(f"Mapper 显然无条件 {tag.upper()}: {path}#{statement_id}")
            elif has_include and not has_condition:
                report.reviews.append(f"Mapper 写语句条件由 include 提供，需 Review: {path}#{statement_id}")


def check_runtime_flyway_config(path: str, text: str, report: Report) -> None:
    """拒绝正式资源中显式开启 clean 或 baseline-on-migrate。"""

    for line_number, line in enumerate(text.splitlines(), start=1):
        value = line.split("#", 1)[0].strip()
        if re.fullmatch(r"(?:spring\.flyway\.)?clean-disabled\s*[:=]\s*false", value, re.IGNORECASE):
            report.errors.append(f"正式配置不得启用 Flyway clean: {path}:{line_number}")
        if re.fullmatch(r"(?:spring\.flyway\.)?baseline-on-migrate\s*[:=]\s*true", value, re.IGNORECASE):
            report.errors.append(f"正式配置不得开启 baseline-on-migrate: {path}:{line_number}")


def git_lines(root: pathlib.Path, *args: str) -> list[str]:
    """执行只读 Git 查询并返回非空行。"""

    output = subprocess.check_output(["git", *args], cwd=root, text=True)
    return [line for line in output.splitlines() if line]


def collect_git_changes(root: pathlib.Path, base: str, head: str) -> tuple[list[tuple[str, ...]], set[str]]:
    """读取 Base/Head 变更和 Base 文件集，不依赖工作区时间戳。"""

    changes = [tuple(line.split("\t")) for line in git_lines(root, "diff", "--name-status", base, head)]
    base_files = set(git_lines(root, "ls-tree", "-r", "--name-only", base))
    return changes, base_files


def run(root: pathlib.Path, base: str | None, head: str, report: Report) -> None:
    """扫描当前树，并在提供 Base 时附加 Git 历史不可变性检查。"""

    tracked = set(git_lines(root, "ls-files"))
    untracked = set(git_lines(root, "ls-files", "--others", "--exclude-standard"))
    files = sorted(tracked | untracked)
    migration_files: list[str] = []
    migration_contents: dict[str, str] = {}
    for relative in files:
        path = root / relative
        if not path.is_file():
            continue
        details = migration_details(relative)
        if details:
            migration_files.append(relative)
            migration_contents[relative] = path.read_text(encoding="utf-8")
        if "/src/main/resources/mapper/" in relative and path.suffix == ".xml":
            check_mapper_xml(relative, path.read_text(encoding="utf-8"), report)
        if "/src/main/resources/" in relative and re.search(r"application.*\.(?:ya?ml|properties)$", relative):
            check_runtime_flyway_config(relative, path.read_text(encoding="utf-8"), report)
    check_migration_paths(migration_files, migration_contents, report)
    if base:
        changes, base_files = collect_git_changes(root, base, head)
        check_versioned_immutability(changes, base_files, report)


def main(argv: list[str] | None = None) -> int:
    """命令行入口；只输出路径和语句 ID，不回显 SQL 内容。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--base")
    parser.add_argument("--head", default="HEAD")
    args = parser.parse_args(argv)
    report = Report()
    try:
        run(args.root.resolve(), args.base, args.head, report)
    except (OSError, subprocess.CalledProcessError) as exc:
        report.errors.append(f"门禁执行失败: {exc}")
    if report.errors:
        print("PERSISTENCE_BASELINE: FAILED")
        for error in report.errors:
            print(f"- {error}")
        return 1
    print("PERSISTENCE_BASELINE: PASSED")
    print("- Flyway naming, path, uniqueness and immutable Base/Head history")
    print("- no cross-service Schema reference in production migrations")
    print("- Mapper XML parsed; dynamic text and unconditional writes checked")
    print("- production Flyway clean/baseline defaults remain safe")
    for review in report.reviews:
        print(f"- REVIEW: {review}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
