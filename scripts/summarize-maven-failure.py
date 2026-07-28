#!/usr/bin/env python3
"""将 Maven/Surefire/Failsafe 失败压缩为适合 AI 诊断的有界摘要。"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

ANSI_PATTERN = re.compile(r"\x1b\[[0-9;]*m")
SECRET_PATTERNS = (
    (re.compile(r"(?i)(authorization:\s*bearer\s+)[^\s]+"), r"\1<redacted>"),
    (re.compile(r"(?i)((?:password|passwd|secret|token|api[_-]?key)\s*[=:]\s*)[^\s,;]+"), r"\1<redacted>"),
    (re.compile(r"(?i)(jdbc:[^\s]+://[^:\s]+:)[^@\s]+(@)"), r"\1<redacted>\2"),
)
SOURCE_PATTERN = re.compile(r"(?:at\s+)?([\w.$]+)\(([^():]+\.java):(\d+)\)")
IMPORTANT_LOG_PATTERN = re.compile(
    r"(?i)(\[ERROR\]|COMPILATION ERROR|BUILD FAILURE|Failed to execute goal|"
    r"Caused by:|Tests run:.*Failures:|There are test failures|"
    r"cannot find symbol|package .* does not exist|Non-resolvable parent POM|"
    r"Could not resolve dependencies|Connection refused|timed out)"
)


@dataclass(frozen=True)
class FailedTest:
    module: str
    report: str
    test_name: str
    failure_type: str
    message: str
    details: str


def clean(text: str) -> str:
    value = ANSI_PATTERN.sub("", text or "")
    for pattern, replacement in SECRET_PATTERNS:
        value = pattern.sub(replacement, value)
    return value.replace("\x00", "")


def compact_lines(text: str, limit: int) -> list[str]:
    result: list[str] = []
    previous = None
    for raw_line in clean(text).splitlines():
        line = raw_line.rstrip()
        if not line:
            continue
        if line == previous:
            continue
        result.append(line)
        previous = line
        if len(result) >= limit:
            break
    return result


def module_from_report(root: Path, report: Path) -> str:
    try:
        relative = report.relative_to(root)
    except ValueError:
        return str(report.parent)
    parts = relative.parts
    if "target" in parts:
        target_index = parts.index("target")
        return str(Path(*parts[:target_index])) or "."
    return str(relative.parent)


def collect_failed_tests(root: Path, max_tests: int) -> list[FailedTest]:
    patterns = (
        "target/surefire-reports/TEST-*.xml",
        "target/failsafe-reports/TEST-*.xml",
    )
    reports: list[Path] = []
    for pattern in patterns:
        reports.extend(root.rglob(pattern))

    failures: list[FailedTest] = []
    for report in sorted(set(reports)):
        try:
            xml_root = ET.parse(report).getroot()
        except (ET.ParseError, OSError):
            continue

        for testcase in xml_root.iter("testcase"):
            failure = testcase.find("failure")
            if failure is None:
                failure = testcase.find("error")
            if failure is None:
                continue

            class_name = testcase.attrib.get("classname", "")
            method_name = testcase.attrib.get("name", "<unknown>")
            test_name = f"{class_name}#{method_name}" if class_name else method_name
            failure_type = failure.attrib.get("type", failure.tag)
            message = failure.attrib.get("message", "")
            details = failure.text or ""
            failures.append(
                FailedTest(
                    module=module_from_report(root, report),
                    report=str(report.relative_to(root)),
                    test_name=test_name,
                    failure_type=failure_type,
                    message=message,
                    details=details,
                )
            )
            if len(failures) >= max_tests:
                return failures
    return failures


def read_log_tail(path: Path, max_bytes: int = 4 * 1024 * 1024) -> list[str]:
    try:
        with path.open("rb") as stream:
            stream.seek(0, 2)
            size = stream.tell()
            stream.seek(max(0, size - max_bytes))
            data = stream.read()
    except OSError:
        return []
    return clean(data.decode("utf-8", errors="replace")).splitlines()


def diagnostic_log_excerpt(lines: list[str], max_lines: int) -> list[str]:
    selected_indexes: set[int] = set()
    for index, line in enumerate(lines):
        if IMPORTANT_LOG_PATTERN.search(line):
            for surrounding in range(max(0, index - 2), min(len(lines), index + 4)):
                selected_indexes.add(surrounding)

    if not selected_indexes:
        start = max(0, len(lines) - max_lines)
        selected_indexes.update(range(start, len(lines)))

    excerpt: list[str] = []
    previous = None
    for index in sorted(selected_indexes):
        line = lines[index].rstrip()
        if not line or line == previous:
            continue
        excerpt.append(line)
        previous = line
        if len(excerpt) >= max_lines:
            break
    return excerpt


def source_hints(chunks: Iterable[str], max_hints: int = 12) -> list[str]:
    hints: list[str] = []
    seen: set[str] = set()
    for chunk in chunks:
        for match in SOURCE_PATTERN.finditer(clean(chunk)):
            hint = f"{match.group(2)}:{match.group(3)}"
            if hint in seen:
                continue
            seen.add(hint)
            hints.append(hint)
            if len(hints) >= max_hints:
                return hints
    return hints


def print_section(title: str, lines: Iterable[str]) -> None:
    values = list(lines)
    if not values:
        return
    print()
    print(f"{title}:")
    for value in values:
        print(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--exit-code", type=int, required=True)
    parser.add_argument("--command", required=True)
    parser.add_argument("--max-tests", type=int, default=3)
    parser.add_argument("--max-detail-lines", type=int, default=40)
    parser.add_argument("--max-log-lines", type=int, default=100)
    args = parser.parse_args()

    root = args.root.resolve()
    log_path = args.log.resolve()
    failures = collect_failed_tests(root, args.max_tests)
    log_lines = read_log_tail(log_path)

    print("MAVEN_RESULT: FAILED")
    print(f"EXIT_CODE: {args.exit_code}")
    print(f"COMMAND: {clean(args.command)}")
    try:
        print(f"FULL_LOG: {log_path.relative_to(root)}")
    except ValueError:
        print(f"FULL_LOG: {log_path}")

    detail_chunks: list[str] = []
    if failures:
        print(f"FAILED_TESTS_SHOWN: {len(failures)}")
        for index, failure in enumerate(failures, start=1):
            print()
            print(f"TEST_{index}: {failure.test_name}")
            print(f"MODULE: {failure.module}")
            print(f"TYPE: {clean(failure.failure_type)}")
            if failure.message:
                print(f"MESSAGE: {clean(failure.message)}")
            print(f"REPORT: {failure.report}")
            details = compact_lines(failure.details, args.max_detail_lines)
            detail_chunks.extend(details)
            print_section("DETAILS", details)
    else:
        print("FAILED_TESTS_SHOWN: 0")
        print_section(
            "MAVEN_DIAGNOSTIC_EXCERPT",
            diagnostic_log_excerpt(log_lines, args.max_log_lines),
        )

    hints = source_hints([*detail_chunks, *log_lines[-2000:]])
    print_section("SOURCE_HINTS", hints)

    print()
    print("NEXT_ACTION:")
    if failures:
        first = failures[0]
        class_name, _, method_name = first.test_name.partition("#")
        module_option = "" if first.module == "." else f"-pl {first.module} "
        test_selector = class_name if not method_name else f"{class_name}#{method_name}"
        print(
            "bash scripts/codex-mvn-test.sh "
            f"{module_option}-Dtest='{test_selector}' "
            "-Dsurefire.failIfNoSpecifiedTests=false test"
        )
    else:
        print("Inspect the first root cause above before rerunning the same command.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
