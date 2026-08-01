#!/usr/bin/env python3
"""验证 MOM 测试生命周期、Smoke 隔离与 CI 结构基线。"""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass


@dataclass(frozen=True)
class Finding:
    rule: str
    path: pathlib.Path
    location: str


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child(element: ET.Element, name: str) -> ET.Element | None:
    return next((item for item in element if local_name(item.tag) == name), None)


def child_text(element: ET.Element | None, name: str) -> str:
    item = child(element, name) if element is not None else None
    return (item.text or "").strip() if item is not None else ""


def plugins(element: ET.Element | None) -> dict[str, ET.Element]:
    result: dict[str, ET.Element] = {}
    if element is None:
        return result
    for plugin in element:
        if local_name(plugin.tag) != "plugin":
            continue
        result[child_text(plugin, "artifactId")] = plugin
    return result


def configured_patterns(plugin: ET.Element, section: str) -> set[str]:
    configuration = child(plugin, "configuration")
    values = child(configuration, section) if configuration is not None else None
    if values is None:
        return set()
    return {(item.text or "").strip() for item in values if (item.text or "").strip()}


def validate_pom(root: pathlib.Path) -> list[Finding]:
    path = root / "pom.xml"
    findings: list[Finding] = []
    try:
        project = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        return [Finding("TB001", path, "project")]

    properties = child(project, "properties")
    versions = {
        local_name(item.tag): (item.text or "").strip()
        for item in (list(properties) if properties is not None else [])
    }
    if versions.get("maven-surefire-plugin.version") != "3.5.4":
        findings.append(Finding("TB001", path, "maven-surefire-plugin.version"))
    if versions.get("maven-failsafe-plugin.version") != "3.5.4":
        findings.append(Finding("TB001", path, "maven-failsafe-plugin.version"))

    build = child(project, "build")
    management = child(child(build, "pluginManagement"), "plugins") if build is not None else None
    managed = plugins(management)
    surefire = managed.get("maven-surefire-plugin")
    failsafe = managed.get("maven-failsafe-plugin")
    if surefire is None or configured_patterns(surefire, "includes") != {
        "**/*Test.java", "**/*Tests.java"
    } or not {"**/*IT.java", "**/*ITCase.java"}.issubset(
        configured_patterns(surefire, "excludes")
    ):
        findings.append(Finding("TB002", path, "maven-surefire-plugin"))
    if failsafe is None or configured_patterns(failsafe, "includes") != {
        "**/*IT.java", "**/*ITCase.java"
    }:
        findings.append(Finding("TB003", path, "maven-failsafe-plugin.includes"))

    active = plugins(child(build, "plugins") if build is not None else None)
    active_failsafe = active.get("maven-failsafe-plugin")
    goals: set[str] = set()
    executions = child(active_failsafe, "executions") if active_failsafe is not None else None
    for execution in list(executions) if executions is not None else []:
        goal_elements = child(execution, "goals")
        goals.update((item.text or "").strip() for item in (
            list(goal_elements) if goal_elements is not None else []
        ))
    if not {"integration-test", "verify"}.issubset(goals):
        findings.append(Finding("TB003", path, "maven-failsafe-plugin.executions"))
    return findings


def strip_java_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", source)


def validate_java_tests(root: pathlib.Path) -> list[Finding]:
    findings: list[Finding] = []
    for path in root.glob("**/src/test/**/*.java"):
        source = path.read_text(encoding="utf-8")
        code = strip_java_comments(source)
        if ("org.testcontainers" in code or "@Testcontainers" in code) \
                and not re.search(r"(?:IT|ITCase)\.java$", path.name):
            findings.append(Finding("TB004", path, "Testcontainers"))
        if re.search(r"\bThread\s*\.\s*sleep\s*\(", code):
            findings.append(Finding("TB005", path, "Thread.sleep"))
        image_literals = re.findall(
            r'(?:DockerImageName\.parse|\w+Container(?:<[^>]+>)?)\s*\(\s*"([^"]+)"',
            code,
        )
        if any(image.rsplit(":", 1)[-1] == "latest" for image in image_literals):
            findings.append(Finding("TB006", path, "container-image:latest"))
    return findings


def workflow_jobs(text: str) -> list[tuple[str, str]]:
    lines = text.splitlines()
    in_jobs = False
    jobs: list[tuple[str, list[str]]] = []
    current: tuple[str, list[str]] | None = None
    for line in lines:
        if line == "jobs:":
            in_jobs = True
            continue
        if in_jobs and line and not line.startswith(" "):
            break
        match = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line) if in_jobs else None
        if match:
            current = (match.group(1), [])
            jobs.append(current)
        elif current is not None:
            current[1].append(line)
    return [(name, "\n".join(lines)) for name, lines in jobs]


def validate_workflows(root: pathlib.Path) -> list[Finding]:
    findings: list[Finding] = []
    for path in (root / ".github/workflows").glob("*.yml"):
        text = path.read_text(encoding="utf-8")
        if "maven.test.skip=true" in text:
            findings.append(Finding("TB012", path, "maven.test.skip"))
        if "needs.changes.outputs" in text and re.search(
                r"^\s*cancel-in-progress:\s*true\s*$", text, re.MULTILINE):
            findings.append(Finding("TB015", path, "concurrency.cancel-in-progress"))
        for job_name, block in workflow_jobs(text):
            if not re.search(r"^    timeout-minutes:\s*[1-9][0-9]*\s*$", block, re.MULTILINE):
                findings.append(Finding("TB007", path, f"jobs.{job_name}.timeout-minutes"))
            if re.search(r"uses:\s*actions/setup-java@", block) and not re.search(
                    r'^\s+java-version:\s*["\']?25["\']?\s*$', block, re.MULTILINE):
                findings.append(Finding("TB013", path, f"jobs.{job_name}.java-version"))
            is_smoke = "smoke" in job_name.lower() or re.search(r"name:\s*.*smoke", block, re.I)
            if is_smoke and not ("actions/upload-artifact@" in block and "if: failure()" in block):
                findings.append(Finding("TB008", path, f"jobs.{job_name}.artifact"))
            if "redis" in job_name.lower():
                needs = re.search(r"^    needs:\s*(.+)$", block, re.MULTILINE)
                if needs and "nacos" in needs.group(1).lower():
                    findings.append(Finding("TB010", path, f"jobs.{job_name}.needs"))
        if re.search(r"nacos[-_ ](?:and[-_ ]|&[-_ ]|)?redis", text, re.I):
            findings.append(Finding("TB009", path, "combined-nacos-redis"))
    return findings


def validate_scripts(root: pathlib.Path) -> list[Finding]:
    findings: list[Finding] = []
    required = [
        "nacos-discovery-smoke.sh",
        "redis-idempotency-smoke.sh",
        "redis-rate-limit-smoke.sh",
    ]
    scripts = root / ".github/scripts"
    if (scripts / "nacos-redis-smoke.sh").exists():
        findings.append(Finding("TB009", scripts / "nacos-redis-smoke.sh", "combined-smoke"))
    for name in required:
        path = scripts / name
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            findings.append(Finding("TB011", path, "missing"))
            continue
        if "set -Eeuo pipefail" not in text or not re.search(r"trap\s+cleanup\s+EXIT", text):
            findings.append(Finding("TB011", path, "strict-mode-or-cleanup"))
    detector = scripts / "detect-ci-scope.sh"
    detector_text = detector.read_text(encoding="utf-8") if detector.exists() else ""
    for output in (
        "nacos", "redis_cache", "redis_idempotency", "redis_rate_limit", "postgresql",
        "messaging", "seata", "observability",
    ):
        if not re.search(rf"emit\s+{output}\b", detector_text):
            findings.append(Finding("TB014", detector, output))
    return findings


def validate(root: pathlib.Path) -> list[Finding]:
    return sorted(
        validate_pom(root)
        + validate_java_tests(root)
        + validate_workflows(root)
        + validate_scripts(root),
        key=lambda item: (str(item.path), item.rule, item.location),
    )


def main() -> int:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    findings = validate(root)
    if findings:
        for finding in findings:
            try:
                path = finding.path.relative_to(root)
            except ValueError:
                path = finding.path
            print(f"{finding.rule} {path}:{finding.location}")
        return 1
    print("TEST_BASELINE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
