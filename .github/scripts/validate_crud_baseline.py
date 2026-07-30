#!/usr/bin/env python3
"""MOM CRUD、分层与多表查询边界的轻量静态门禁。"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

SERVER_JAVA = re.compile(r"^mom-[^/]+-platform/mom-[^/]+-server/src/main/java/.+\.java$")
MAPPER_INTERFACE = re.compile(r"\b(?:public\s+)?interface\s+(\w+Mapper)\b([^\{]*)\{")
IMPORT = re.compile(r"^\s*import\s+([^;]+);", re.M)
CRUD_METHOD = re.compile(r"\b(insert|deleteById|deleteByIds|updateById|selectById|selectBatchIds|selectList|selectPage|selectCount)\s*\(")
FORBIDDEN_DOMAIN_IMPORT = re.compile(
    r"^(?:org\.springframework\.(?:web|jdbc|data\.redis)|com\.baomidou\.mybatisplus|org\.apache\.ibatis|"
    r"org\.springframework\.cloud\.openfeign|jakarta\.servlet)\."
)
QUERY_MAPPER_NAME = re.compile(r"\binterface\s+\w+QueryMapper\b")

# 阶段一发现的精确历史语句；阶段二修复后必须删除，禁止增加文件或通配符。
SELECT_STAR_HISTORY = {
    ("mom-iam-platform/mom-iam-server/src/main/resources/mapper/iam/IamExternalUserBindingMapper.xml", "selectEffectiveByUserId"),
}

APPLICATION_EXCEPTIONS = {
    "mom-mdm-platform/mom-mdm-server/src/main/java/io/github/chrisshi/mom/mdm/application/MdmDataProbeService.java",
    "mom-mdm-platform/mom-mdm-server/src/main/java/io/github/chrisshi/mom/mdm/application/MdmOutboxProbeService.java",
    "mom-mdm-platform/mom-mdm-server/src/main/java/io/github/chrisshi/mom/mdm/application/MdmSeataAtLocalParticipantService.java",
    "mom-mdm-platform/mom-mdm-server/src/main/java/io/github/chrisshi/mom/mdm/application/MdmSeataAtProbeService.java",
    "mom-integration-platform/mom-integration-server/src/main/java/io/github/chrisshi/mom/integration/application/IntegrationSeataAtParticipantService.java",
}


@dataclass
class Report:
    """保存阻断项与需人工复核的精确候选。"""

    errors: list[str] = field(default_factory=list)
    reviews: list[str] = field(default_factory=list)


def imports(text: str) -> set[str]:
    """提取 Java import，避免以任意字符串命中包名。"""

    return set(IMPORT.findall(text))


def check_java_file(path: str, text: str, report: Report) -> None:
    """检查 Mapper、Entity、Controller、Application、Domain 和查询边界。"""

    if not SERVER_JAVA.fullmatch(path):
        return
    imported = imports(text)
    pure = pathlib.PurePosixPath(path)

    if re.search(r"\b(?:IService|ServiceImpl)\s*<", text) or any(
        item.startswith("com.baomidou.mybatisplus.extension.service.") for item in imported
    ):
        report.errors.append(f"禁止 MyBatis-Plus 通用 Service: {path}")

    if pure.name.endswith("Entity.java") and re.search(r"(?m)^\s*@Data\b", text):
        report.errors.append(f"持久化 Entity 禁止 Lombok @Data: {path}")

    mapper = MAPPER_INTERFACE.search(text)
    if mapper and mapper.group(1) != "MomBaseMapper":
        is_query = QUERY_MAPPER_NAME.search(text) is not None
        if is_query:
            if "/query/" not in path.replace("\\", "/"):
                report.errors.append(f"Query Mapper 必须位于明确 query 包: {path}")
            if re.search(r"\b(?:List|Page|Optional)?\s*<?\s*\w+Entity\b", text):
                report.errors.append(f"Query Mapper 必须返回 Row/Projection，禁止 Entity: {path}")
        elif "MomBaseMapper<" not in mapper.group(2):
            report.errors.append(f"正式 Mapper 默认必须继承 MomBaseMapper: {path}")
        if CRUD_METHOD.search(text):
            report.errors.append(f"Mapper 重复声明 BaseMapper 普通 CRUD: {path}")

    if pure.name.endswith("Controller.java"):
        bad = sorted(item for item in imported if (
            item.rsplit(".", 1)[-1].endswith(("Mapper", "Repository"))
            or (item.rsplit(".", 1)[-1].endswith("Entity") and item.rsplit(".", 1)[-1] != "ResponseEntity")
        ))
        if bad:
            report.errors.append(f"Controller 禁止依赖持久化类型: {path} -> {bad[0]}")
        if "org.springframework.transaction.annotation.Transactional" in imported:
            report.errors.append(f"Controller 禁止声明事务: {path}")

    if "/application/" in path and path not in APPLICATION_EXCEPTIONS:
        bad = sorted(item for item in imported if ".infrastructure.persistence." in item or item.endswith("Mapper"))
        if bad:
            report.errors.append(f"Application 禁止直接依赖 Mapper/Entity: {path} -> {bad[0]}")

    if "/domain/" in path:
        bad = sorted(item for item in imported if FORBIDDEN_DOMAIN_IMPORT.match(item) or ".infrastructure." in item)
        if bad:
            report.errors.append(f"Domain 禁止依赖 Web/持久化/基础设施: {path} -> {bad[0]}")

    if re.search(r"\b(for|while)\s*\([^)]*\)\s*\{[^{}]{0,800}\b\w+Mapper\.", text, re.S):
        report.reviews.append(f"循环 Mapper 调用疑似 N+1，需 Review: {path}")
    if re.search(r"\.in\s*\([^,]+,\s*\w+\s*\)", text) and not re.search(r"MAX_|batch|chunk|limit", text, re.I):
        report.reviews.append(f"IN 参数上限无法静态证明，需 Review: {path}")


def check_mapper_xml(path: str, text: str, report: Report) -> None:
    """检查普通 CRUD XML 与多表查询高置信风险。"""

    try:
        root = ET.fromstring(text)
    except ET.ParseError as exc:
        report.errors.append(f"Mapper XML 无法解析: {path}: {exc}")
        return
    for node in root.iter():
        tag = node.tag.rsplit("}", 1)[-1]
        if tag not in {"select", "insert", "update", "delete"}:
            continue
        statement_id = node.attrib.get("id", "<missing-id>")
        sql = " ".join(node.itertext())
        if re.search(r"\bSELECT\s+\*(?:\s+|$)", sql, re.I | re.S) and (path, statement_id) not in SELECT_STAR_HISTORY:
            report.errors.append(f"多表/自定义查询禁止 SELECT *: {path}#{statement_id}")
        elif re.search(r"\bSELECT\s+\*(?:\s+|$)", sql, re.I | re.S):
            report.reviews.append(f"精确历史 SELECT *，阶段二必须修复: {path}#{statement_id}")
        if "${" in sql:
            report.errors.append(f"多表/自定义查询禁止客户端动态 SQL: {path}#{statement_id}")
        if re.search(r"\bmom_[a-z][a-z0-9_]*\s*\.", sql, re.I):
            report.errors.append(f"Query Mapper 禁止限定 Schema 或跨 Schema: {path}#{statement_id}")
        if re.search(r"\bJOIN\b", sql, re.I) and re.search(r"\b(?:LIMIT|OFFSET)\b", sql, re.I):
            report.reviews.append(f"JOIN 后分页需证明分页对象与基数: {path}#{statement_id}")
        children = list(node.iter())
        has_foreach = "foreach" in {c.tag.rsplit("}", 1)[-1] for c in children}
        foreach_in = any(re.search(r"\bIN\s*\(", c.attrib.get("open", ""), re.I) for c in children)
        if tag == "select" and has_foreach and (re.search(r"\bIN\s*\(", sql, re.I) or foreach_in):
            report.reviews.append(f"批量 IN 上限需 Review: {path}#{statement_id}")


def git_files(root: pathlib.Path) -> list[str]:
    """列出当前工作树文件。"""

    tracked = subprocess.check_output(["git", "ls-files"], cwd=root, text=True).splitlines()
    untracked = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=root, text=True).splitlines()
    return sorted(set(tracked) | set(untracked))


def run(root: pathlib.Path, report: Report) -> None:
    """扫描全部正式 Server Java 与 Mapper XML。"""

    for relative in git_files(root):
        path = root / relative
        if not path.is_file():
            continue
        if SERVER_JAVA.fullmatch(relative):
            check_java_file(relative, path.read_text(encoding="utf-8"), report)
        elif "/src/main/resources/mapper/" in relative and relative.endswith(".xml"):
            check_mapper_xml(relative, path.read_text(encoding="utf-8"), report)


def main(argv: list[str] | None = None) -> int:
    """命令行入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path.cwd())
    args = parser.parse_args(argv)
    report = Report()
    try:
        run(args.root.resolve(), report)
    except (OSError, subprocess.CalledProcessError) as exc:
        report.errors.append(f"CRUD 门禁执行失败: {exc}")
    if report.errors:
        print("CRUD_BASELINE: FAILED")
        for error in report.errors:
            print(f"- {error}")
        return 1
    print("CRUD_BASELINE: PASSED")
    print("- Mapper/Entity/Controller/Application/Domain boundaries checked")
    print("- generic services, duplicate CRUD and unsafe query SQL rejected")
    for review in report.reviews:
        print(f"- REVIEW: {review}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
