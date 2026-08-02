#!/usr/bin/env python3
"""国际化静态门禁的正反例测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("validate_localization_baseline.py")
SPEC = importlib.util.spec_from_file_location("localization_baseline", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class LocalizationBaselineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_java(self, relative: str, body: str) -> None:
        path = self.root / relative / "src/main/java/example/Sample.java"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")

    def rules(self) -> set[str]:
        return {rule for _, rule in MODULE.validate(self.root)}

    def test_compliant_instant_and_explicit_zone_pass(self) -> None:
        self.write_java("mom-demo-server", "class Sample { Instant at; ZoneId zone = ZoneId.of(\"Asia/Shanghai\"); }")
        self.assertEqual(set(), self.rules())

    def test_default_locale_is_rejected(self) -> None:
        self.write_java("mom-demo-server", "class Sample { Object x = Locale.getDefault(); }")
        self.assertIn("LB001", self.rules())

    def test_system_zone_is_rejected(self) -> None:
        self.write_java("mom-demo-server", "class Sample { Object x = ZoneId.systemDefault(); }")
        self.assertIn("LB002", self.rules())

    def test_legacy_default_timezone_is_rejected(self) -> None:
        self.write_java("mom-demo-server", "class Sample { Object x = TimeZone.getDefault(); }")
        self.assertIn("LB003", self.rules())

    def test_local_datetime_field_is_rejected(self) -> None:
        self.write_java("mom-demo-server", "record Sample(LocalDateTime occurredAt) {}")
        self.assertIn("LB004", self.rules())

    def test_simple_date_format_is_rejected(self) -> None:
        self.write_java("mom-demo-server", "class Sample { SimpleDateFormat value; }")
        self.assertIn("LB005", self.rules())

    def test_public_double_quantity_is_rejected(self) -> None:
        self.write_java("mom-demo-api", "record Sample(double quantity) {}")
        self.assertIn("LB006", self.rules())

    def test_public_long_id_is_rejected(self) -> None:
        self.write_java("mom-demo-api", "record Sample(Long materialId) {}")
        self.assertIn("LB007", self.rules())

    def test_cron_without_zone_is_rejected(self) -> None:
        self.write_java("mom-demo-server", 'class Sample { @Scheduled(cron = "0 0 * * * *") void run() {} }')
        self.assertIn("LB008", self.rules())

    def test_fixed_delay_does_not_require_zone(self) -> None:
        self.write_java("mom-demo-server", "class Sample { @Scheduled(fixedDelay = 10) void run() {} }")
        self.assertNotIn("LB008", self.rules())

    def test_comments_and_literals_do_not_trigger_java_rules(self) -> None:
        self.write_java("mom-demo-server", 'class Sample { String text = "Locale.getDefault() LocalDateTime value"; /* TimeZone.getDefault() */ }')
        self.assertEqual(set(), self.rules())

    def test_test_sources_are_excluded(self) -> None:
        path = self.root / "mom-demo/src/test/java/example/Sample.java"
        path.parent.mkdir(parents=True)
        path.write_text("class Sample { Object x = ZoneId.systemDefault(); }", encoding="utf-8")
        self.assertEqual(set(), self.rules())

    def write_bundle(self, name: str, content: str) -> None:
        path = self.root / "mom-demo/src/main/resources/i18n" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_paired_bundle_with_same_keys_passes(self) -> None:
        self.write_bundle("messages_zh-CN.properties", "hello=你好\n")
        self.write_bundle("messages_en-US.properties", "hello=Hello\n")
        self.assertEqual(set(), self.rules())

    def test_underscore_tag_is_rejected(self) -> None:
        path = self.root / "mom-demo/src/main/resources/application.yml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("app.locale: zh_CN\n", encoding="utf-8")
        self.assertIn("LB009", self.rules())

    def test_java_resource_bundle_underscore_naming_is_supported(self) -> None:
        self.write_bundle("messages_zh_CN.properties", "hello=你好\n")
        self.write_bundle("messages_en_US.properties", "hello=Hello\n")
        self.assertEqual(set(), self.rules())

    def test_missing_language_pair_is_rejected(self) -> None:
        self.write_bundle("messages_zh-CN.properties", "hello=你好\n")
        self.assertIn("LB010", self.rules())

    def test_bundle_key_mismatch_is_rejected(self) -> None:
        self.write_bundle("messages_zh-CN.properties", "hello=你好\nbye=再见\n")
        self.write_bundle("messages_en-US.properties", "hello=Hello\n")
        self.assertIn("LB011", self.rules())


if __name__ == "__main__":
    unittest.main(verbosity=2)
