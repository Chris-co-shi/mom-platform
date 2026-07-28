#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT_DIR"

python3 - "$@" <<'PY'
from __future__ import annotations

import ipaddress
import pathlib
import re
import sys
from dataclasses import dataclass


MAX_FINDINGS = 80
SENSITIVE_SUFFIXES = (
    "PASSWORD",
    "SECRET",
    "TOKEN",
    "PRIVATE_KEY",
    "HMAC_PEPPER",
    "API_KEY",
)
DANGEROUS_ENABLE_FLAGS = {
    "IAM_BOOTSTRAP_ENABLED",
    "NACOS_DISCOVERY_ENABLED",
    "OTEL_METRICS_EXPORT_ENABLED",
    "OTEL_TRACING_EXPORT_ENABLED",
    "IAM_ALLOW_TEST_KEY",
    "IAM_ALLOW_LOCAL_REFRESH_PEPPER",
}
DANGEROUS_YAML_PATHS = {
    "mom.iam.bootstrap.enabled",
    "spring.cloud.nacos.discovery.enabled",
    "management.otlp.metrics.export.enabled",
    "management.tracing.export.otlp.enabled",
    "mom.iam.authorization.key.allow-test-key",
    "mom.iam.session.allow-local-pepper",
}
SENSITIVE_YAML_KEYS = {
    "password",
    "secret",
    "token",
    "private-key",
    "private_key",
    "hmac-pepper",
    "hmac_pepper",
    "api-key",
    "api_key",
}
PLACEHOLDER_PATTERN = re.compile(r"\$\{([A-Z][A-Z0-9_]*)(?::([^{}]*))?}")
IPV4_PATTERN = re.compile(r"(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])")
YAML_ENTRY_PATTERN = re.compile(r"^(\s*)([A-Za-z0-9_.-]+):(?:\s*(.*))?$")


@dataclass(frozen=True)
class Finding:
    line: int
    rule: str


def is_sensitive_environment_name(name: str) -> bool:
    return any(name == suffix or name.endswith("_" + suffix) for suffix in SENSITIVE_SUFFIXES)


def normalized_scalar(value: str | None) -> str:
    if value is None:
        return ""
    return value.split("#", 1)[0].strip().strip("\"'")


def is_empty_or_placeholder(value: str | None) -> bool:
    scalar = normalized_scalar(value)
    return scalar in {"", "null", "~", "<PLACEHOLDER>"}


def is_nonlocal_private_address(raw: str) -> bool:
    try:
        address = ipaddress.ip_address(raw)
    except ValueError:
        return False
    cgnat = address in ipaddress.ip_network("100.64.0.0/10")
    return not address.is_loopback and (address.is_private or cgnat)


def yaml_path_and_value(lines: list[str]) -> list[tuple[int, str, str]]:
    stack: list[tuple[int, str]] = []
    result: list[tuple[int, str, str]] = []
    for number, raw_line in enumerate(lines, 1):
        if not raw_line.strip() or raw_line.lstrip().startswith(("#", "-")):
            continue
        match = YAML_ENTRY_PATTERN.match(raw_line)
        if not match:
            continue
        indent = len(match.group(1).replace("\t", "    "))
        key = match.group(2)
        value = match.group(3) or ""
        while stack and stack[-1][0] >= indent:
            stack.pop()
        path = ".".join([entry[1] for entry in stack] + [key])
        result.append((number, path, value))
        if not normalized_scalar(value):
            stack.append((indent, key))
    return result


def scan_text(text: str, production_profile: bool = False) -> list[Finding]:
    findings: list[Finding] = []
    lines = text.splitlines()
    for number, line in enumerate(lines, 1):
        for match in PLACEHOLDER_PATTERN.finditer(line):
            name, default = match.group(1), match.group(2)
            if is_sensitive_environment_name(name) and not is_empty_or_placeholder(default):
                findings.append(Finding(number, "sensitive-env-nonempty-default"))
            if name in DANGEROUS_ENABLE_FLAGS and normalized_scalar(default).lower() == "true":
                findings.append(Finding(number, "unsafe-feature-enabled-by-default"))
        for candidate in IPV4_PATTERN.findall(line):
            if is_nonlocal_private_address(candidate):
                findings.append(Finding(number, "nonlocal-private-address-default"))

    for number, path, value in yaml_path_and_value(lines):
        key = path.rsplit(".", 1)[-1].lower()
        scalar = normalized_scalar(value)
        if key in SENSITIVE_YAML_KEYS and "${" not in value and not is_empty_or_placeholder(value):
            findings.append(Finding(number, "fixed-sensitive-value"))
        if path in DANGEROUS_YAML_PATHS and scalar.lower() == "true":
            findings.append(Finding(number, "unsafe-feature-enabled-by-default"))
        if path == "mom.iam.authorization.key.private-key-location" and "/test/" in scalar.lower():
            findings.append(Finding(number, "test-key-in-runtime-defaults"))
        if production_profile:
            if path in DANGEROUS_YAML_PATHS and scalar.lower() == "true":
                findings.append(Finding(number, "unsafe-production-profile-setting"))
            if path == "mom.iam.session.hmac-pepper" and not is_empty_or_placeholder(value):
                findings.append(Finding(number, "production-local-pepper"))
            if "/test/" in scalar.lower():
                findings.append(Finding(number, "test-key-in-production-profile"))
    return list(dict.fromkeys(findings))


def runtime_config_files(root: pathlib.Path) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for path in root.glob("mom-*/**/src/main/resources/application*.y*ml"):
        name = path.name.lower()
        if name in {"application-local.yml", "application-local.yaml",
                    "application-test.yml", "application-test.yaml"}:
            continue
        files.append(path)
    return sorted(files)


def is_production_profile(path: pathlib.Path) -> bool:
    return path.name.lower() in {
        "application-prod.yml",
        "application-prod.yaml",
        "application-production.yml",
        "application-production.yaml",
    }


def self_test() -> None:
    safe = """
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      password: ${REDIS_PASSWORD:}
  cloud:
    nacos:
      discovery:
        enabled: ${NACOS_DISCOVERY_ENABLED:false}
management:
  tracing:
    export:
      otlp:
        enabled: ${OTEL_TRACING_EXPORT_ENABLED:false}
mom:
  iam:
    session:
      hmac-pepper: ${IAM_REFRESH_HMAC_PEPPER:}
"""
    unsafe_cases = {
        "sensitive-env-nonempty-default": "password: ${POSTGRES_PASSWORD:<NON_EMPTY>}",
        "nonlocal-private-address-default": "host: 192.168.1.10",
        "unsafe-feature-enabled-by-default": "enabled: ${IAM_BOOTSTRAP_ENABLED:true}",
        "fixed-sensitive-value": "password: fixed-non-empty",
        "test-key-in-runtime-defaults": (
            "mom:\n  iam:\n    authorization:\n      key:\n"
            "        private-key-location: classpath:keys/test/example.pem"
        ),
    }
    if scan_text(safe):
        raise AssertionError("安全正例被错误拒绝")
    for expected_rule, sample in unsafe_cases.items():
        rules = {finding.rule for finding in scan_text(sample)}
        if expected_rule not in rules:
            raise AssertionError(f"反例未触发规则: {expected_rule}")
    print("SECURE_DEFAULTS_SELF_TEST: PASSED")


def main() -> int:
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return 0
    if sys.argv[1:]:
        print("usage: validate-secure-defaults.sh [--self-test]", file=sys.stderr)
        return 2

    root = pathlib.Path.cwd()
    findings: list[tuple[pathlib.Path, Finding]] = []
    for path in runtime_config_files(root):
        text = path.read_text(encoding="utf-8")
        for finding in scan_text(text, is_production_profile(path)):
            findings.append((path, finding))

    main_resource_keys = sorted(
        path for path in root.glob("mom-*/**/src/main/resources/**/*")
        if path.is_file() and path.suffix.lower() in {".pem", ".key", ".p12", ".jks"}
    )
    for path in main_resource_keys:
        findings.append((path, Finding(1, "key-material-in-main-resources")))

    if findings:
        print("SECURE_DEFAULTS: FAILED")
        for path, finding in findings[:MAX_FINDINGS]:
            print(f"- {path.relative_to(root)}:{finding.line}: {finding.rule}")
        if len(findings) > MAX_FINDINGS:
            print(f"- additional-findings-omitted: {len(findings) - MAX_FINDINGS}")
        return 1

    print("SECURE_DEFAULTS: PASSED")
    print(f"- runtime configuration files checked: {len(runtime_config_files(root))}")
    print("- test/local resources and Testcontainers fixtures excluded")
    return 0


raise SystemExit(main())
PY
