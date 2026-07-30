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
    ".github/scripts/validate-persistence-baseline.sh",
    ".github/scripts/validate_persistence_baseline.py",
    ".github/scripts/validate-runtime-security-baseline.sh",
    ".github/scripts/validate_runtime_security_baseline.py",
    ".github/scripts/test_validate_runtime_security_baseline.py",
    ".github/scripts/validate-test-baseline.sh",
    ".github/scripts/validate_test_baseline.py",
    ".github/scripts/test_validate_test_baseline.py",
    ".github/scripts/validate-localization-baseline.sh",
    ".github/scripts/validate_localization_baseline.py",
    ".github/scripts/test_validate_localization_baseline.py",
    ".github/scripts/validate-crud-baseline.sh",
    ".github/scripts/validate_crud_baseline.py",
    ".github/scripts/test_validate_crud_baseline.py",
    ".github/scripts/validate-schema-design-baseline.sh",
    ".github/scripts/validate_schema_design_baseline.py",
    ".github/scripts/test_validate_schema_design_baseline.py",
    ".github/scripts/validate-no-business-foreign-key.sh",
    ".github/scripts/validate_no_business_foreign_key.py",
    ".github/scripts/test_validate_no_business_foreign_key.py",
    ".github/scripts/nacos-discovery-smoke.sh",
    ".github/scripts/redis-idempotency-smoke.sh",
    ".github/scripts/redis-rate-limit-smoke.sh",
    "docs/engineering/codex-local-workflow.md",
    "docs/engineering/standards/official-source-policy.md",
    "docs/engineering/standards/jdk-25-engineering-standard.md",
    "docs/engineering/standards/spring-boot-4.1-engineering-standard.md",
    "docs/engineering/standards/spring-cloud-2025.1-engineering-standard.md",
    "docs/engineering/standards/spring-cloud-alibaba-2025.1-engineering-standard.md",
    "docs/engineering/standards/module-layering-standard.md",
    "docs/engineering/standards/http-api-contract-standard.md",
    "docs/engineering/standards/api-evolution-idempotency-standard.md",
    "docs/engineering/standards/persistence-data-modeling-standard.md",
    "docs/engineering/standards/crud-application-standard.md",
    "docs/engineering/standards/multi-table-association-query-standard.md",
    "docs/engineering/standards/database-schema-design-standard.md",
    "docs/engineering/templates/table-design-record-template.md",
    "docs/engineering/templates/multi-table-query-design-template.md",
    "docs/engineering/templates/crud-slice-acceptance-template.md",
    "docs/adr/ADR-026-MOM业务表禁止物理外键与关联完整性策略.md",
    "docs/engineering/standards/transaction-consistency-standard.md",
    "docs/engineering/standards/audit-concurrency-lifecycle-standard.md",
    "docs/engineering/standards/configuration-profile-secret-standard.md",
    "docs/engineering/standards/security-protocol-runtime-standard.md",
    "docs/engineering/standards/outbound-http-client-standard.md",
    "docs/engineering/standards/redis-key-ttl-failure-standard.md",
    "docs/engineering/standards/testing-strategy-standard.md",
    "docs/engineering/standards/maven-test-lifecycle-standard.md",
    "docs/engineering/standards/testcontainers-smoke-acceptance-standard.md",
    "docs/engineering/standards/ci-scope-quality-gate-standard.md",
    "docs/engineering/standards/localization-locale-standard.md",
    "docs/engineering/standards/timezone-date-time-standard.md",
    "docs/engineering/standards/number-money-rounding-standard.md",
    "docs/engineering/standards/measurement-unit-standard.md",
    "docs/engineering/standards/user-preference-standard.md",
    "docs/engineering/P1.6-S02-持久化历史例外清单.md",
    "docs/adr/ADR-020-PostgreSQL物理Schema命名空间.md",
    "docs/engineering/P1.6-S03-安全配置历史例外清单.md",
    "docs/adr/ADR-021-运行时配置来源与Secret边界.md",
    "docs/engineering/P1.6-S04-测试与CI历史例外清单.md",
    "docs/adr/ADR-022-测试分层与CI质量门禁.md",
    "docs/engineering/P1.6-S05-国际化与个性化现状清单.md",
    "docs/adr/ADR-023-Locale时区与用户偏好边界.md",
    "mom-architecture-tests/pom.xml",
    "mom-architecture-tests/src/test/java/io/github/chrisshi/mom/architecture/MavenModuleDependencyArchitectureTest.java",
    "mom-architecture-tests/src/test/java/io/github/chrisshi/mom/architecture/ServerPackageArchitectureTest.java",
    "mom-architecture-tests/src/test/java/io/github/chrisshi/mom/architecture/PersistenceArchitectureTest.java",
    "mom-architecture-tests/src/test/java/io/github/chrisshi/mom/architecture/RuntimeSecurityArchitectureTest.java",
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
        is_failsafe_test = relative.endswith(("IT.java", "ITCase.java"))
        external_runtime_markers = (
            "@Testcontainers", "PostgreSQLContainer", "GenericContainer<",
            "DockerImageName", "@DynamicPropertySource",
        )
        mutating_sql = "JdbcTemplate" in text and re.search(
            r"\b(?:jdbc|jdbcTemplate)\.(?:update|execute|batchUpdate)\s*\(", text
        )
        if not is_failsafe_test and (
                any(marker in text for marker in external_runtime_markers) or mutating_sql):
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
print("- IAM Surefire tests contain no external data-store integration tests; Failsafe *IT/*ITCase remains allowed")
print("- S01/S02/S03/S04/S05 standards and Maven architecture-test module are wired")
PY

bash .github/scripts/validate-crud-baseline.sh
bash .github/scripts/validate-schema-design-baseline.sh
bash .github/scripts/validate-no-business-foreign-key.sh
