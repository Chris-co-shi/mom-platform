#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT_DIR"

python3 - <<'PY'
from __future__ import annotations

import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

root = pathlib.Path.cwd()
errors: list[str] = []
notes: list[str] = []

required_files = [
    "scripts/codex-mvn-test.sh",
    "scripts/summarize-maven-failure.py",
    "scripts/codex-doctor.sh",
    "scripts/codex-verify-changed.sh",
    ".github/scripts/validate-secure-defaults.sh",
    "docs/engineering/codex-local-workflow.md",
    "docs/engineering/standards/official-source-policy.md",
    "docs/engineering/standards/jdk-25-engineering-standard.md",
    "docs/engineering/standards/spring-boot-4.1-engineering-standard.md",
    "docs/engineering/standards/spring-cloud-2025.1-engineering-standard.md",
    "docs/engineering/standards/spring-cloud-alibaba-2025.1-engineering-standard.md",
    "docs/engineering/standards/module-layering-standard.md",
    "docs/engineering/standards/http-api-contract-standard.md",
    "docs/engineering/standards/api-evolution-idempotency-standard.md",
    "mom-architecture-tests/pom.xml",
    "mom-architecture-tests/src/test/java/io/github/chrisshi/mom/architecture/MavenModuleDependencyArchitectureTest.java",
    "mom-architecture-tests/src/test/java/io/github/chrisshi/mom/architecture/ServerPackageArchitectureTest.java",
]
for relative in required_files:
    if not (root / relative).is_file():
        errors.append(f"missing required engineering file: {relative}")

pom = root / "pom.xml"
try:
    tree = ET.parse(pom)
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    props = tree.getroot().find("m:properties", ns)
    if props is None:
        raise ValueError("root properties not found")
    values = {child.tag.rsplit("}", 1)[-1]: (child.text or "").strip() for child in props}
except Exception as exc:
    errors.append(f"cannot parse root pom.xml: {exc}")
    values = {}

try:
    modules = {
        (module.text or "").strip()
        for module in tree.getroot().findall("m:modules/m:module", ns)
    }
    if "mom-architecture-tests" not in modules:
        errors.append("mom-architecture-tests must be included in the root Maven reactor")
except Exception as exc:
    errors.append(f"cannot validate architecture-test reactor wiring: {exc}")

expected_exact = {"java.version": "25"}
for key, expected in expected_exact.items():
    actual = values.get(key)
    if actual != expected:
        errors.append(f"{key} must be {expected}, found {actual!r}")

release_value = values.get("maven.compiler.release")
if release_value not in {"25", "${java.version}"}:
    errors.append(f"maven.compiler.release must resolve to Java 25, found {release_value!r}")

expected_prefixes = {
    "spring-boot.version": "4.1.",
    "spring-cloud.version": "2025.1.",
    "spring-cloud-alibaba.version": "2025.1.",
}
for key, prefix in expected_prefixes.items():
    actual = values.get(key, "")
    if not actual.startswith(prefix):
        errors.append(f"{key} must use {prefix}x, found {actual!r}")
    else:
        notes.append(f"{key}={actual}")

try:
    tracked = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
except Exception as exc:
    errors.append(f"cannot list tracked files: {exc}")
    tracked = []

config_false_patterns = [
    re.compile(r"spring\.cloud\.compatibility-verifier\.enabled\s*[:=]\s*false", re.I),
    re.compile(r"spring\.cloud\.nacos\.config\.import-check\.enabled\s*[:=]\s*false", re.I),
]
deprecated_nacos_patterns = [
    re.compile(r"spring\.cloud\.nacos\.config\.(shared-configs|extension-configs)", re.I),
]
preview_pattern = re.compile(r"--enable-preview")
module_escape_pattern = re.compile(r"--add-(opens|exports)")
internal_import_pattern = re.compile(r"^\s*import\s+(sun\.|jdk\.internal\.)", re.M)

for relative in filter(None, tracked):
    path = root / relative
    if not path.is_file():
        continue
    lower_name = path.name.lower()
    if lower_name in {"bootstrap.yml", "bootstrap.yaml", "bootstrap.properties"}:
        errors.append(f"Spring Cloud Alibaba 2025.1.x forbids legacy bootstrap file: {relative}")

    if relative.startswith("docs/") or relative == ".github/scripts/validate-engineering-baseline.sh":
        continue
    if path.suffix.lower() not in {".java", ".xml", ".yml", ".yaml", ".properties", ".sh"}:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue

    if preview_pattern.search(text):
        errors.append(f"preview features require an ADR and dedicated profile, found in: {relative}")
    if module_escape_pattern.search(text):
        errors.append(f"JDK module encapsulation bypass requires an ADR, found in: {relative}")
    if path.suffix.lower() == ".java" and internal_import_pattern.search(text):
        errors.append(f"JDK internal API import is forbidden: {relative}")
    for pattern in config_false_patterns:
        if pattern.search(text):
            errors.append(f"official compatibility/import verification must not be disabled: {relative}")
    for pattern in deprecated_nacos_patterns:
        if pattern.search(text):
            errors.append(f"use spring.config.import instead of deprecated Nacos config lists: {relative}")

    iam_test_prefix = "mom-iam-platform/mom-iam-server/src/test/java/"
    if relative.startswith(iam_test_prefix):
        if relative.endswith("IntegrationTest.java"):
            errors.append(f"IAM default Surefire scope must not contain IntegrationTest: {relative}")
        external_runtime_markers = (
            "@Testcontainers", "PostgreSQLContainer", "GenericContainer<",
            "DockerImageName", "@DynamicPropertySource",
        )
        mutating_sql = "JdbcTemplate" in text and re.search(
            r"\b(?:jdbc|jdbcTemplate)\.(?:update|execute|batchUpdate)\s*\(", text
        )
        if any(marker in text for marker in external_runtime_markers) or mutating_sql:
            errors.append(f"IAM unit-test scope must not start or mutate external data stores: {relative}")

if errors:
    print("ENGINEERING_BASELINE: FAILED")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("ENGINEERING_BASELINE: PASSED")
print("- Java 25 release baseline")
for note in notes:
    print(f"- {note}")
print("- no preview/internal API/module-escape violations")
print("- no legacy Nacos bootstrap or disabled compatibility checks")
print("- IAM default tests contain no external data-store integration tests")
print("- S01 standards and Maven architecture-test module are wired")
PY
