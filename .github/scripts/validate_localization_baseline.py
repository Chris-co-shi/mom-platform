#!/usr/bin/env python3
"""对生产源码执行可解释的 Locale、时区和数值契约静态基线检查。"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys
from collections import defaultdict


CALL_RULES = (
    ("LB001", re.compile(r"\bLocale\s*\.\s*getDefault\s*\(")),
    ("LB002", re.compile(r"\bZoneId\s*\.\s*systemDefault\s*\(")),
    ("LB003", re.compile(r"\bTimeZone\s*\.\s*getDefault\s*\(")),
    ("LB005", re.compile(r"\bSimpleDateFormat\b")),
)
DECIMAL_NAMES = r"(?:amount|price|cost|quantity|weight|volume|concentration|ratio|percentage|rate)"


def strip_java_comments_and_literals(text: str) -> str:
    """保留换行并移除注释、字符和字符串内容，避免示例文本误报。"""
    output: list[str] = []
    index = 0
    state = "code"
    while index < len(text):
        char = text[index]
        next_char = text[index + 1] if index + 1 < len(text) else ""
        if state == "code":
            if char == "/" and next_char == "/":
                output.extend("  ")
                index += 2
                state = "line"
                continue
            if char == "/" and next_char == "*":
                output.extend("  ")
                index += 2
                state = "block"
                continue
            if char == '"':
                output.append(" ")
                index += 1
                state = "string"
                continue
            if char == "'":
                output.append(" ")
                index += 1
                state = "char"
                continue
            output.append(char)
            index += 1
            continue
        if state == "line":
            if char == "\n":
                output.append("\n")
                state = "code"
            else:
                output.append(" ")
            index += 1
            continue
        if state == "block":
            if char == "*" and next_char == "/":
                output.extend("  ")
                index += 2
                state = "code"
            else:
                output.append("\n" if char == "\n" else " ")
                index += 1
            continue
        if char == "\\":
            output.append(" ")
            if next_char:
                output.append("\n" if next_char == "\n" else " ")
                index += 2
            else:
                index += 1
            continue
        if (state == "string" and char == '"') or (state == "char" and char == "'"):
            output.append(" ")
            index += 1
            state = "code"
        else:
            output.append("\n" if char == "\n" else " ")
            index += 1
    return "".join(output)


def production_java(root: pathlib.Path) -> list[pathlib.Path]:
    return sorted(path for path in root.rglob("src/main/java/**/*.java") if "/target/" not in path.as_posix())


def is_api_module(path: pathlib.Path) -> bool:
    return any(part.endswith("-api") for part in path.parts)


def java_findings(root: pathlib.Path) -> list[tuple[str, str]]:
    findings: list[tuple[str, str]] = []
    for path in production_java(root):
        relative = path.relative_to(root).as_posix()
        code = strip_java_comments_and_literals(path.read_text(encoding="utf-8"))
        for rule, pattern in CALL_RULES:
            if pattern.search(code):
                findings.append((relative, rule))
        if re.search(
            r"\bLocalDateTime\s+(?:\w*(?:At|Time|Timestamp|Occurred|Created|Updated|Completed|Started|Ended))\w*\s*[;,)=]",
            code,
            re.I,
        ):
            findings.append((relative, "LB004"))
        if is_api_module(path):
            if re.search(rf"\b(?:double|float|Double|Float)\s+{DECIMAL_NAMES}\w*\b", code, re.I):
                findings.append((relative, "LB006"))
            if re.search(r"\bLong\s+(?:id|\w+Id)\b", code):
                findings.append((relative, "LB007"))
        for annotation in re.findall(r"@Scheduled\s*\((.*?)\)", code, flags=re.S):
            if re.search(r"\bcron\s*=", annotation) and not re.search(r"\bzone\s*=", annotation):
                findings.append((relative, "LB008"))
    return findings


def parse_properties(path: pathlib.Path) -> set[str]:
    keys: set[str] = set()
    continuation = ""
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = continuation + raw
        if line.endswith("\\") and not line.endswith("\\\\"):
            continuation = line[:-1]
            continue
        continuation = ""
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        match = re.match(r"([^:=\s]+)\s*[:=\s]", stripped)
        if match:
            keys.add(match.group(1).replace("\\ ", " "))
    return keys


def configured_locale_findings(root: pathlib.Path) -> list[tuple[str, str]]:
    findings: list[tuple[str, str]] = []
    value_pattern = re.compile(r"^\s*(?:[\w.-]*locale|[\w.-]*language)\s*[:=]\s*['\"]?([A-Za-z]{2,3}_[A-Za-z]{2,4})['\"]?\s*(?:#.*)?$", re.I)
    for suffix in ("*.yml", "*.yaml", "*.properties"):
        for path in root.rglob(f"src/main/resources/**/{suffix}"):
            for line in path.read_text(encoding="utf-8").splitlines():
                if value_pattern.match(line):
                    findings.append((path.relative_to(root).as_posix(), "LB009"))
                    break
    return findings


def bundle_findings(root: pathlib.Path) -> list[tuple[str, str]]:
    groups: dict[tuple[pathlib.Path, str], dict[str, pathlib.Path]] = defaultdict(dict)
    for path in root.rglob("src/main/resources/**/*.properties"):
        match = re.fullmatch(r"(.+)_(zh_CN|zh-CN|en_US|en-US)\.properties", path.name)
        if match:
            canonical = match.group(2).replace("_", "-")
            groups[(path.parent, match.group(1))][canonical] = path
    findings: list[tuple[str, str]] = []
    for variants in groups.values():
        zh = variants.get("zh-CN")
        en = variants.get("en-US")
        anchor = zh or en or next(iter(variants.values()))
        if zh is None or en is None:
            findings.append((anchor.relative_to(root).as_posix(), "LB010"))
        elif parse_properties(zh) != parse_properties(en):
            findings.append((anchor.relative_to(root).as_posix(), "LB011"))
    return findings


def validate(root: pathlib.Path) -> list[tuple[str, str]]:
    return sorted(set(java_findings(root) + configured_locale_findings(root) + bundle_findings(root)))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, required=True)
    args = parser.parse_args()
    findings = validate(args.root.resolve())
    if findings:
        print("LOCALIZATION_BASELINE: FAILED")
        for path, rule in findings:
            print(f"- {path} {rule}")
        return 1
    print("LOCALIZATION_BASELINE: PASSED")
    print("- production Locale/time-zone calls and time-point types")
    print("- public decimal/ID candidate types and scheduled cron zones")
    print("- zh-CN/en-US resource bundle pairing and key alignment")
    return 0


if __name__ == "__main__":
    sys.exit(main())
