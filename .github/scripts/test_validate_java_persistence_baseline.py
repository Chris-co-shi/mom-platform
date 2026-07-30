#!/usr/bin/env python3
"""validate_java_persistence_baseline.py 的正反例测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest

SCRIPT = pathlib.Path(__file__).with_name("validate_java_persistence_baseline.py")
SPEC = importlib.util.spec_from_file_location("java_persistence_baseline", SCRIPT)
baseline = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = baseline
SPEC.loader.exec_module(baseline)


class JavaPersistenceBaselineTest(unittest.TestCase):
    def report(self):
        return baseline.Report()

    def test_direct_jdbc_in_system_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/i18n/JdbcRepository.java"
        )
        baseline.check_java_file(path, "import org.springframework.jdbc.core.JdbcTemplate;", report)
        self.assertTrue(any("禁止直接 JDBC" in item for item in report.errors))

    def test_precise_sas_jdbc_exception_is_allowed(self):
        report = self.report()
        path = next(item for item in baseline.DIRECT_JDBC_EXCEPTIONS if "IamAuthorization" in item)
        baseline.check_java_file(path, "import org.springframework.jdbc.core.JdbcTemplate;", report)
        self.assertEqual([], report.errors)

    def test_select_star_in_new_mapper_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/parameter/NewMapper.java"
        )
        text = 'import org.apache.ibatis.annotations.Select;\n@Select("SELECT * FROM system_parameter")'
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("SELECT *" in item for item in report.errors))

    def test_explicit_columns_are_allowed(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/parameter/SystemParameterMapper.java"
        )
        text = 'import org.apache.ibatis.annotations.Select;\n@Select("SELECT id FROM system_parameter")'
        baseline.check_java_file(path, text, report)
        self.assertEqual([], report.errors)

    def test_mybatis_dynamic_text_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/parameter/NewMapper.java"
        )
        text = 'import org.apache.ibatis.annotations.Select;\n@Select("SELECT id ORDER BY ${column}")'
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("动态文本" in item for item in report.errors))


if __name__ == "__main__":
    unittest.main()
