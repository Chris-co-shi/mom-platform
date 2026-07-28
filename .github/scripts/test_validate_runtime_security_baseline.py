#!/usr/bin/env python3
"""运行时安全配置门禁正反例测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("validate_runtime_security_baseline.py")
SPEC = importlib.util.spec_from_file_location("runtime_security_baseline", MODULE_PATH)
assert SPEC and SPEC.loader
baseline = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = baseline
SPEC.loader.exec_module(baseline)


class RuntimeSecurityBaselineTest(unittest.TestCase):
    """覆盖 Profile、Secret、Nacos、Actuator、CORS、超时和动态刷新规则。"""

    def check(self, yaml: str, relative: str = "service/src/main/resources/application.yml"):
        report = baseline.Report()
        documents = baseline.parse_yaml_documents(yaml)
        base = pathlib.PurePosixPath(relative).name == "application.yml"
        for document in documents:
            baseline.check_document(relative, document, base, report)
        return report

    def rules(self, report):
        return {finding.rule for finding in report.findings}

    def test_base_profile_activation_is_rejected(self):
        report = self.check("spring:\n  profiles:\n    active: local\n")
        self.assertIn("RS001", self.rules(report))

    def test_safe_base_configuration_is_accepted(self):
        report = self.check("spring:\n  data:\n    redis:\n      password: ${REDIS_PASSWORD:}\n      timeout: 2s\n")
        self.assertEqual([], report.findings)

    def test_production_bootstrap_is_rejected(self):
        report = self.check("mom:\n  iam:\n    bootstrap:\n      enabled: true\n", "service/src/main/resources/application-prod.yml")
        self.assertIn("RS002", self.rules(report))

    def test_production_test_key_and_local_pepper_are_rejected(self):
        report = self.check("""mom:
  iam:
    authorization:
      key:
        allow-test-key: true
    session:
      allow-local-pepper: true
""", "service/src/main/resources/application-production.yml")
        self.assertTrue({"RS003", "RS004"}.issubset(self.rules(report)))

    def test_production_insecure_cookie_is_rejected(self):
        report = self.check("server:\n  servlet:\n    session:\n      cookie:\n        secure: false\n", "service/src/main/resources/application-prod.yml")
        self.assertIn("RS005", self.rules(report))

    def test_production_dangerous_actuator_is_rejected(self):
        report = self.check("management:\n  endpoints:\n    web:\n      exposure:\n        include: health,env,heapdump\n", "service/src/main/resources/application-prod.yml")
        self.assertIn("RS006", self.rules(report))

    def test_production_technical_probe_is_rejected(self):
        report = self.check("mom:\n  technical-probe:\n    enabled: true\n", "service/src/main/resources/application-prod.yml")
        self.assertIn("RS007", self.rules(report))

    def test_non_empty_secret_default_is_rejected_without_leaking_value(self):
        report = self.check("spring:\n  datasource:\n    password: ${POSTGRES_PASSWORD:fixture-value}\n")
        self.assertIn("RS008", self.rules(report))
        self.assertNotIn("fixture-value", repr(report.findings))

    def test_disabled_compatibility_checks_are_rejected(self):
        report = self.check("""spring:
  cloud:
    compatibility-verifier:
      enabled: false
    nacos:
      config:
        import-check:
          enabled: false
""")
        self.assertTrue({"RS009", "RS010"}.issubset(self.rules(report)))

    def test_wildcard_redirect_is_rejected(self):
        report = self.check("mom:\n  iam:\n    redirect-uri: https://*.example.test/callback\n")
        self.assertIn("RS012", self.rules(report))

    def test_cors_wildcard_with_credentials_is_rejected(self):
        report = self.check("""mom:
  cors:
    allowed-origins:
      - "*"
    allow-credentials: true
""")
        self.assertIn("RS013", self.rules(report))

    def test_unbounded_timeout_is_rejected(self):
        report = self.check("spring:\n  data:\n    redis:\n      timeout: ${REDIS_TIMEOUT:0}\n")
        self.assertIn("RS014", self.rules(report))

    def test_full_feign_logging_is_rejected_in_base(self):
        report = self.check("spring:\n  cloud:\n    openfeign:\n      client:\n        config:\n          mdm:\n            logger-level: FULL\n")
        self.assertIn("RS015", self.rules(report))

    def test_production_test_private_key_path_is_rejected(self):
        report = self.check("mom:\n  iam:\n    private-key-location: classpath:keys/test/private.pem\n", "service/src/main/resources/application-prod.yml")
        self.assertIn("RS016", self.rules(report))

    def test_nacos_secret_default_is_rejected(self):
        report = self.check("spring:\n  cloud:\n    nacos:\n      config:\n        secret-key: ${NACOS_SECRET_KEY:fixture}\n")
        self.assertTrue({"RS008", "RS017"}.issubset(self.rules(report)))

    def test_sensitive_nacos_refresh_is_rejected(self):
        report = self.check("spring:\n  config:\n    import:\n      - nacos:iam-security.yml?refreshEnabled=true\n")
        self.assertIn("RS018", self.rules(report))

    def test_non_sensitive_nacos_refresh_is_allowed(self):
        report = self.check("spring:\n  config:\n    import:\n      - nacos:ui-tuning.yml?refreshEnabled=true\n")
        self.assertEqual([], report.findings)

    def test_production_gateway_security_disable_is_rejected(self):
        report = self.check("mom:\n  gateway:\n    security:\n      enabled: false\n", "service/src/main/resources/application-production.yml")
        self.assertIn("RS019", self.rules(report))

    def test_test_resource_contract_is_not_a_runtime_input(self):
        relative = "service/src/test/resources/application-prod.yml"
        self.assertNotIn("/src/main/resources/", relative)

    def test_java_security_refresh_scope_is_rejected(self):
        report = baseline.Report()
        baseline.check_java_refresh(
            "service/src/main/java/example/security/IamSecurityProperties.java",
            "@RefreshScope class IamSecurityProperties {}",
            report,
        )
        self.assertIn("RS018", self.rules(report))


if __name__ == "__main__":
    unittest.main()
