#!/usr/bin/env python3
"""MOM Profile、Secret、Nacos、Actuator、超时与安全配置轻量门禁。"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys
from dataclasses import dataclass, field

CONFIG_NAME = re.compile(r"application(?:-[^.]+)?\.(?:ya?ml|properties)$", re.IGNORECASE)
PLACEHOLDER = re.compile(r"^\$\{[^}:]+(?::(?P<default>.*))?}$")
TIMEOUT_KEYS = ("timeout", "connect-timeout", "read-timeout", "response-timeout")
SECRET_PROPERTY_NAMES = {
    "password", "secret", "private-key", "private_key", "private-key-location",
    "private_key_location", "hmac-pepper", "hmac_pepper", "access-key", "access_key",
    "secret-key", "secret_key", "client-secret", "client_secret",
}
DANGEROUS_ACTUATOR = {
    "env", "configprops", "loggers", "heapdump", "threaddump", "mappings", "shutdown"
}
SECURITY_REFRESH_HINTS = (
    "password", "secret", "pepper", "jwk", "issuer", "redirect", "cors", "permission",
    "security", "datasource", "flyway", "seata", "schema", "oauth", "session", "token",
)


@dataclass
class Finding:
    """只保存可公开的文件、属性路径和规则编号。"""

    rule: str
    path: str
    property_path: str


@dataclass
class Report:
    """保存阻断项；绝不保存配置值。"""

    findings: list[Finding] = field(default_factory=list)

    def add(self, rule: str, path: str, property_path: str) -> None:
        self.findings.append(Finding(rule, path, property_path))


def strip_yaml_comment(line: str) -> str:
    """移除引号外 YAML 注释，避免从注释示例产生误报。"""

    quote: str | None = None
    escaped = False
    result: list[str] = []
    for char in line:
        if escaped:
            result.append(char)
            escaped = False
            continue
        if char == "\\" and quote == '"':
            result.append(char)
            escaped = True
            continue
        if char in {"'", '"'}:
            quote = None if quote == char else (char if quote is None else quote)
        if char == "#" and quote is None:
            break
        result.append(char)
    return "".join(result)


def scalar(value: str) -> str:
    """去除 YAML 标量的外围引号，仅用于结构判断。"""

    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def parse_yaml_documents(text: str) -> list[dict[str, list[str]]]:
    """解析本项目 application YAML 的缩进键路径；不尝试解释锚点或任意 YAML 类型。"""

    documents: list[dict[str, list[str]]] = [dict()]
    stack: list[tuple[int, str]] = []
    for raw in text.splitlines():
        cleaned = strip_yaml_comment(raw).rstrip()
        if not cleaned.strip():
            continue
        if cleaned.strip() == "---":
            documents.append({})
            stack.clear()
            continue
        indent = len(cleaned) - len(cleaned.lstrip(" "))
        content = cleaned.strip()
        if content.startswith("- "):
            if stack:
                path = ".".join(item[1] for item in stack)
                documents[-1].setdefault(path, []).append(scalar(content[2:]))
            continue
        match = re.match(r"(?P<key>[A-Za-z0-9_.-]+)\s*:\s*(?P<value>.*)$", content)
        if not match:
            continue
        while stack and stack[-1][0] >= indent:
            stack.pop()
        key = match.group("key")
        value = match.group("value").strip()
        prefix = [item[1] for item in stack]
        path = ".".join([*prefix, key])
        if value:
            documents[-1].setdefault(path, []).append(scalar(value))
        else:
            stack.append((indent, key))
    return documents


def parse_properties(text: str) -> list[dict[str, list[str]]]:
    """解析 Java Properties 的键和值，不支持的续行按单行保守处理。"""

    result: dict[str, list[str]] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        match = re.match(r"([^:=\s]+)\s*[:=]\s*(.*)$", line)
        if match:
            result.setdefault(match.group(1), []).append(match.group(2).strip())
    return [result]


def bool_value(values: list[str] | None) -> bool | None:
    """读取唯一布尔含义；占位符默认值也参与判断。"""

    if not values:
        return None
    value = values[-1].strip().lower()
    placeholder = PLACEHOLDER.fullmatch(value)
    if placeholder and placeholder.group("default") is not None:
        value = placeholder.group("default").strip().lower()
    if value == "true":
        return True
    if value == "false":
        return False
    return None


def effective_default(value: str) -> str | None:
    """返回占位符或字面量的默认值；无默认占位符返回 None。"""

    match = PLACEHOLDER.fullmatch(value.strip())
    if match:
        return match.group("default")
    return scalar(value)


def is_secret_path(path: str) -> bool:
    """按完整属性段识别 Secret，避免把 minimum-password-length 等策略误判为值。"""

    return path.lower().rsplit(".", 1)[-1] in SECRET_PROPERTY_NAMES


def is_production(relative: str, document: dict[str, list[str]]) -> bool:
    """识别 application-prod/production 或文档内 on-profile。"""

    name = pathlib.PurePosixPath(relative).name.lower()
    if re.search(r"application-(?:prod|production)(?:\.|-)", name):
        return True
    profiles = document.get("spring.config.activate.on-profile", [])
    return any(re.search(r"(?:^|[,|\s])(prod|production)(?:$|[,|\s])", item, re.I) for item in profiles)


def check_document(relative: str, document: dict[str, list[str]], base: bool, report: Report) -> None:
    """执行属性级规则；输出不包含值。"""

    production = is_production(relative, document)
    if base and "spring.profiles.active" in document:
        report.add("RS001", relative, "spring.profiles.active")
    if production and bool_value(document.get("mom.iam.bootstrap.enabled")) is True:
        report.add("RS002", relative, "mom.iam.bootstrap.enabled")
    if production and bool_value(document.get("mom.iam.authorization.key.allow-test-key")) is True:
        report.add("RS003", relative, "mom.iam.authorization.key.allow-test-key")
    if production and bool_value(document.get("mom.iam.session.allow-local-pepper")) is True:
        report.add("RS004", relative, "mom.iam.session.allow-local-pepper")
    if production and bool_value(document.get("server.servlet.session.cookie.secure")) is False:
        report.add("RS005", relative, "server.servlet.session.cookie.secure")
    if production:
        for value in document.get("management.endpoints.web.exposure.include", []):
            for endpoint in {part.strip().lower() for part in value.split(",")} & DANGEROUS_ACTUATOR:
                report.add("RS006", relative, f"management.endpoints.web.exposure.include[{endpoint}]")
        for path in document:
            if (path.endswith("technical-probe.enabled") or path.endswith("technical-probe")) \
                    and bool_value(document.get(path)) is True:
                report.add("RS007", relative, path)
        if bool_value(document.get("mom.gateway.security.enabled")) is False:
            report.add("RS019", relative, "mom.gateway.security.enabled")

    for path, values in document.items():
        lower = path.lower()
        if is_secret_path(lower):
            for value in values:
                default = effective_default(value)
                if default is not None and default.strip() not in {"", "null", "~"}:
                    report.add("RS008", relative, path)
                    break
        if lower in {
            "spring.cloud.compatibility-verifier.enabled",
            "spring.cloud.compatibility-verifier.compatibility-verifier.enabled",
        } and bool_value(values) is False:
            report.add("RS009", relative, path)
        if lower == "spring.cloud.nacos.config.import-check.enabled" and bool_value(values) is False:
            report.add("RS010", relative, path)
        if "redirect-uri" in lower:
            if any("*" in value for value in values):
                report.add("RS012", relative, path)
        if lower.endswith(TIMEOUT_KEYS):
            for value in values:
                default = (effective_default(value) or "").strip().lower()
                if default in {"0", "0ms", "0s", "-1", "-1ms", "-1s", "infinite", "infinity"}:
                    report.add("RS014", relative, path)
                    break
        if lower.endswith("logger-level") and any((effective_default(value) or "").lower() == "full" for value in values):
            if base or production:
                report.add("RS015", relative, path)
        if production and "private-key" in lower:
            if any("test" in (effective_default(value) or "").lower() for value in values):
                report.add("RS016", relative, path)

    origins = [value for path, values in document.items() if "cors" in path.lower() and "origin" in path.lower() for value in values]
    credentials = any(
        bool_value(values) is True for path, values in document.items()
        if "cors" in path.lower() and (path.lower().endswith("allow-credentials") or path.lower().endswith("allowed-credentials"))
    )
    if credentials and any(value.strip() == "*" for value in origins):
        report.add("RS013", relative, "cors.allowed-origins+allow-credentials")

    imports = document.get("spring.config.import", [])
    for item in imports:
        lower = item.lower()
        if "nacos:" in lower and "refreshenabled=true" in lower \
                and any(hint in lower for hint in SECURITY_REFRESH_HINTS):
            report.add("RS018", relative, "spring.config.import")
    for path, values in document.items():
        lower = path.lower()
        if lower.startswith("spring.cloud.nacos.config") and is_secret_path(lower):
            if any((effective_default(value) or "").strip() for value in values):
                report.add("RS017", relative, path)


def check_java_refresh(relative: str, text: str, report: Report) -> None:
    """将安全配置上的 RefreshScope 作为精确阻断候选，不分析一般 Java 依赖语义。"""

    if "@RefreshScope" not in text:
        return
    lower = relative.lower()
    if any(hint in lower for hint in SECURITY_REFRESH_HINTS):
        report.add("RS018", relative, "@RefreshScope")


def tracked_files(root: pathlib.Path) -> list[str]:
    """返回已跟踪和未跟踪文件，保证本地新增门禁也可自检。"""

    tracked = subprocess.check_output(["git", "ls-files"], cwd=root, text=True).splitlines()
    untracked = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard"], cwd=root, text=True
    ).splitlines()
    return sorted(set(tracked) | set(untracked))


def run(root: pathlib.Path, report: Report) -> None:
    """扫描正式主资源和生产 Java；测试资源、文档和 fixtures 明确排除。"""

    for relative in tracked_files(root):
        path = root / relative
        if not path.is_file():
            continue
        lower_name = path.name.lower()
        if "/src/main/resources/" in relative and lower_name in {
            "bootstrap.yml", "bootstrap.yaml", "bootstrap.properties"
        }:
            report.add("RS011", relative, "bootstrap")
        if "/src/main/resources/" in relative and CONFIG_NAME.fullmatch(path.name):
            text = path.read_text(encoding="utf-8")
            docs = parse_properties(text) if path.suffix.lower() == ".properties" else parse_yaml_documents(text)
            base = path.name.lower() in {"application.yml", "application.yaml", "application.properties"}
            for document in docs:
                check_document(relative, document, base, report)
        if "/src/main/java/" in relative and path.suffix == ".java":
            check_java_refresh(relative, path.read_text(encoding="utf-8"), report)


def main(argv: list[str] | None = None) -> int:
    """命令行入口。"""

    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path.cwd())
    args = parser.parse_args(argv)
    report = Report()
    try:
        run(args.root.resolve(), report)
    except (OSError, subprocess.CalledProcessError) as exc:
        print("RUNTIME_SECURITY_BASELINE: FAILED")
        print(f"- RS000 | <gate> | {exc.__class__.__name__}")
        return 1
    if report.findings:
        print("RUNTIME_SECURITY_BASELINE: FAILED")
        for finding in sorted(report.findings, key=lambda item: (item.path, item.rule, item.property_path)):
            print(f"- {finding.rule} | {finding.path} | {finding.property_path}")
        return 1
    print("RUNTIME_SECURITY_BASELINE: PASSED")
    print("- base/profile, Secret, Nacos, Actuator, CORS, timeout and refresh boundaries")
    print("- findings expose only rule, file and property path")
    return 0


if __name__ == "__main__":
    sys.exit(main())
