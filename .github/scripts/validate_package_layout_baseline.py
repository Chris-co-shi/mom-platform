#!/usr/bin/env python3
"""MOM bounded context Server Package 与 Mapper XML 布局门禁。"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

MAIN_JAVA = re.compile(r"^mom-[^/]+-platform/mom-[^/]+-server/src/main/java/(.+)\.java$")
TEST_JAVA = re.compile(r"^mom-[^/]+-platform/mom-[^/]+-server/src/test/java/(.+)\.java$")
MAPPER_XML = re.compile(r"^mom-[^/]+-platform/mom-[^/]+-server/src/main/resources/mapper/.+\.xml$")
PACKAGE = re.compile(r"(?m)^\s*package\s+([a-zA-Z_$][\w$]*(?:\.[a-zA-Z_$][\w$]*)*)\s*;")
TYPE = re.compile(r"\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)\b")
FQCN = re.compile(r"^(?:[a-zA-Z_$][\w$]*\.)+[A-Za-z_$][\w$]*$")
ALLOWED_INFRASTRUCTURE = {"persistence", "client", "messaging", "cache", "storage"}
ALLOWED_PERSISTENCE = {"entity", "mapper", "repository", "query", "converter", "typehandler"}

# S15-E 阶段一只允许这些逐文件存量；阶段二移动完成后必须删除，禁止新增条目。
LEGACY_LAYOUT_EXCEPTIONS = {
    "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/admin/IamAdminReadModelRepository.java",
    "mom-mdm-platform/mom-mdm-server/src/main/java/io/github/chrisshi/mom/mdm/infrastructure/persistence/MdmDataProbeEntity.java",
    "mom-mdm-platform/mom-mdm-server/src/main/java/io/github/chrisshi/mom/mdm/infrastructure/persistence/MdmDataProbeMapper.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/dictionary/MybatisSystemDictionaryItemRepository.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/dictionary/MybatisSystemDictionaryRepository.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/dictionary/SystemDictionaryEntity.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/dictionary/SystemDictionaryItemEntity.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/dictionary/SystemDictionaryItemMapper.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/dictionary/SystemDictionaryMapper.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/i18n/MybatisSystemI18nRepository.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/i18n/SystemI18nMessageEntity.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/i18n/SystemI18nMessageMapper.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/i18n/SystemI18nReleaseEntity.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/i18n/SystemI18nReleaseHistoryRow.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/i18n/SystemI18nReleaseMapper.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/i18n/SystemI18nResourceEntity.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/i18n/SystemI18nResourceMapper.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/parameter/MybatisSystemParameterRepository.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/parameter/SystemParameterEntity.java",
    "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/infrastructure/persistence/parameter/SystemParameterMapper.java",
    "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/infrastructure/persistence/repository/admin/IamAuthorizationAssignmentRepository.java",
    "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/infrastructure/persistence/repository/admin/IamClientPolicyAdminRepository.java",
    "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/infrastructure/persistence/repository/admin/IamRoleAdminRepository.java",
    "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/infrastructure/persistence/repository/admin/IamSecurityAuditQueryRepository.java",
    "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/infrastructure/persistence/repository/admin/IamSessionAdminRepository.java",
    "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/infrastructure/persistence/repository/admin/IamUserAccessAdminRepository.java",
    "mom-iam-platform/mom-iam-server/src/main/java/io/github/chrisshi/mom/iam/infrastructure/persistence/repository/admin/IamUserAdminRepository.java",
}


@dataclass
class JavaType:
    """记录一个正式 Java 类型的文件、包和简单类名。"""

    path: str
    package: str
    name: str
    text: str

    @property
    def fqcn(self) -> str:
        """返回完整类名。"""

        return f"{self.package}.{self.name}"


@dataclass
class Report:
    """区分阻断错误、人工复核候选和逐文件迁移例外。"""

    errors: list[str] = field(default_factory=list)
    reviews: list[str] = field(default_factory=list)
    exceptions: list[str] = field(default_factory=list)


def without_comments_and_literals(text: str) -> str:
    """移除注释与字符串，避免示例文字触发类型分类。"""

    pattern = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/', re.S)
    return pattern.sub(lambda match: " " * len(match.group(0)), text)


def package_for_path(relative: str, source_match: re.Match[str]) -> str:
    """根据源码根后的目录计算期望 package。"""

    return pathlib.PurePosixPath(source_match.group(1)).parent.as_posix().replace("/", ".")


def classify(java: JavaType) -> str | None:
    """以高置信特征区分 Entity、普通 Mapper、Query Mapper 和 Repository Adapter。"""

    clean = without_comments_and_literals(java.text)
    if java.name.endswith("QueryMapper"):
        return "query"
    if "@TableName" in clean or (java.name.endswith("Entity") and ".infrastructure." in java.package):
        return "entity"
    if re.search(r"\b(?:MomBaseMapper|BaseMapper)\s*<", clean) or (
        "@Mapper" in clean and java.name.endswith("Mapper")
    ):
        return "mapper"
    if re.match(r"(?:Mybatis|Postgresql).+Repository$", java.name):
        return "repository"
    if java.name.endswith(("QueryRepository", "ReadModelRepository")) and "Mapper" in clean:
        return "query"
    return None


def violation(report: Report, relative: str, message: str) -> None:
    """只将阶段一逐文件存量降为迁移例外，新文件和扩大范围仍阻断。"""

    if relative in LEGACY_LAYOUT_EXCEPTIONS:
        report.exceptions.append(f"{relative}: {message}")
    else:
        report.errors.append(f"{relative}: {message}")


def check_java(relative: str, text: str, report: Report) -> JavaType | None:
    """检查源码路径、package、职责包、Feature-first 和配置候选。"""

    match = MAIN_JAVA.fullmatch(relative) or TEST_JAVA.fullmatch(relative)
    if not match:
        return None
    declared = PACKAGE.search(text)
    if not declared:
        report.errors.append(f"{relative}: 缺少 package 声明")
        return None
    package = declared.group(1)
    expected = package_for_path(relative, match)
    if package != expected:
        report.errors.append(f"{relative}: package={package} 与路径期望 {expected} 不一致")
    type_match = TYPE.search(without_comments_and_literals(text))
    if not type_match or relative.endswith("package-info.java"):
        return None
    java = JavaType(relative, package, type_match.group(1), text)
    if TEST_JAVA.fullmatch(relative):
        return java

    marker = ".infrastructure."
    if marker in package:
        remainder = package.split(marker, 1)[1].split(".")
        first = remainder[0]
        if first not in ALLOWED_INFRASTRUCTURE:
            violation(report, relative, f"Infrastructure 第一层必须为 Adapter 类型，发现 {first}")
        if first == "persistence" and len(remainder) > 1 and remainder[1] not in ALLOWED_PERSISTENCE:
            violation(report, relative, f"Persistence 禁止 Feature-first 子包 {remainder[1]}")
        if remainder[:2] == ["persistence", "repository"] and len(remainder) > 2:
            violation(report, relative, f"Repository Adapter 必须扁平归位，发现子包 {remainder[2]}")
        if remainder[:2] == ["persistence", "configuration"]:
            violation(report, relative, "bounded context Configuration 应迁至顶层 configuration")

    kind = classify(java)
    expected_suffix = {
        "entity": ".infrastructure.persistence.entity",
        "mapper": ".infrastructure.persistence.mapper",
        "repository": ".infrastructure.persistence.repository",
        "query": ".infrastructure.persistence.query",
    }.get(kind)
    if expected_suffix and package != package.split(".infrastructure", 1)[0] + expected_suffix:
        violation(report, relative, f"{kind} 类型必须位于 {expected_suffix}")

    clean = without_comments_and_literals(text)
    if "@Configuration" in clean and package.endswith(".infrastructure.configuration"):
        violation(report, relative, "正式服务 Spring Configuration 不应位于 infrastructure.configuration")
    if java.name.endswith("Repository") and "Mapper" in clean and kind is None:
        report.reviews.append(f"{relative}: 注入 Mapper 的 Repository 职责需人工确认")
    return java


def check_xml(relative: str, text: str, known: dict[str, JavaType], namespaces: dict[str, str], report: Report) -> None:
    """验证 Mapper XML 的 Namespace、类型字符串和孤立资源。"""

    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        report.errors.append(f"{relative}: XML 无法解析: {exc}")
        return
    namespace = root.attrib.get("namespace", "").strip()
    if not namespace:
        report.errors.append(f"{relative}: Mapper XML 缺少 namespace")
        return
    if namespace in namespaces:
        report.errors.append(f"{relative}: namespace 与 {namespaces[namespace]} 重复: {namespace}")
    namespaces[namespace] = relative
    mapper = known.get(namespace)
    if mapper is None:
        report.errors.append(f"{relative}: namespace 指向不存在的 Java 类型: {namespace}")
    else:
        if pathlib.PurePosixPath(relative).stem != mapper.name:
            report.errors.append(f"{relative}: XML 文件名必须与 Mapper 接口 {mapper.name} 一致")
        expected = ".infrastructure.persistence.query" if mapper.name.endswith("QueryMapper") else ".infrastructure.persistence.mapper"
        if not mapper.package.endswith(expected):
            violation(report, mapper.path, f"XML namespace 对应 Mapper 必须位于 {expected}")

    for node in root.iter():
        for attribute in ("resultType", "typeHandler"):
            value = node.attrib.get(attribute, "").strip()
            outer = value.split("$", 1)[0]
            if value and FQCN.fullmatch(value.replace("$", ".")) and value.startswith("io.github.chrisshi.mom.") and outer not in known:
                report.errors.append(f"{relative}: {attribute} 指向不存在的 Java 类型: {value}")


def git_files(root: pathlib.Path) -> list[str]:
    """读取 tracked 与当前新增文件，确保门禁可在提交前运行。"""

    tracked = subprocess.check_output(["git", "ls-files"], cwd=root, text=True).splitlines()
    untracked = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=root, text=True
    ).splitlines()
    return sorted(set(tracked) | set(untracked))


def run(root: pathlib.Path, report: Report, files: list[str] | None = None) -> None:
    """扫描全部正式 Server 主源码、测试源码和 Mapper XML。"""

    entries = files if files is not None else git_files(root)
    known: dict[str, JavaType] = {}
    simple_names: dict[tuple[str, str], str] = {}
    xml_entries: list[tuple[str, str]] = []
    for relative in entries:
        path = root / relative
        if not path.is_file():
            continue
        if MAIN_JAVA.fullmatch(relative) or TEST_JAVA.fullmatch(relative):
            java = check_java(relative, path.read_text(encoding="utf-8"), report)
            if java is not None:
                if java.fqcn in known:
                    report.errors.append(f"{relative}: 完整类名与 {known[java.fqcn].path} 重复: {java.fqcn}")
                known[java.fqcn] = java
                parts = java.package.split(".")
                context = ".".join(parts[:5]) if len(parts) >= 5 else java.package
                key = (context, java.name)
                if key in simple_names and simple_names[key] != relative:
                    report.reviews.append(f"{relative}: bounded context 内简单类名与 {simple_names[key]} 冲突: {java.name}")
                simple_names[key] = relative
        elif MAPPER_XML.fullmatch(relative):
            xml_entries.append((relative, path.read_text(encoding="utf-8")))
    namespaces: dict[str, str] = {}
    for relative, text in xml_entries:
        check_xml(relative, text, known, namespaces, report)


def main(argv: list[str] | None = None) -> int:
    """命令行入口；任何高置信违规均以非零状态阻断。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path.cwd())
    args = parser.parse_args(argv)
    report = Report()
    try:
        run(args.root.resolve(), report)
    except (OSError, subprocess.CalledProcessError) as exc:
        report.errors.append(f"Package Layout 门禁执行失败: {exc}")
    if report.errors:
        print("PACKAGE_LAYOUT_BASELINE: FAILED")
        for error in report.errors:
            print(f"- {error}")
        return 1
    print("PACKAGE_LAYOUT_BASELINE: PASSED")
    print("- Java 路径/package、Persistence 职责包与 Mapper XML 引用已检查")
    for exception in report.exceptions:
        print(f"- MIGRATION_EXCEPTION: {exception}")
    for review in report.reviews:
        print(f"- REVIEW: {review}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
