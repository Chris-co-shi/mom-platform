#!/usr/bin/env python3
"""Package Layout Baseline 正例、负例与误报边界测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import tempfile
import unittest

PATH = pathlib.Path(__file__).with_name("validate_package_layout_baseline.py")
SPEC = importlib.util.spec_from_file_location("package_layout", PATH)
module = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class PackageLayoutBaselineTest(unittest.TestCase):
    """覆盖渐进式职责归位、Feature-first、XML、Fixture 与类名冲突。"""

    base = "mom-demo-platform/mom-demo-server/src/main/java/io/github/chrisshi/mom/demo/"

    def check(self, suffix: str, text: str):
        report = module.Report()
        java = module.check_java(self.base + suffix, text, report)
        return java, report

    def test_level1_entity_is_allowed(self):
        _, report = self.check(
            "infrastructure/entity/DemoOrderEntity.java",
            "package io.github.chrisshi.mom.demo.infrastructure.entity; @TableName class DemoOrderEntity {}",
        )
        self.assertEqual([], report.errors)

    def test_level1_mapper_is_allowed(self):
        _, report = self.check(
            "infrastructure/mapper/DemoOrderMapper.java",
            "package io.github.chrisshi.mom.demo.infrastructure.mapper; @Mapper interface DemoOrderMapper extends MomBaseMapper<DemoOrderEntity> {}",
        )
        self.assertEqual([], report.errors)

    def test_level1_query_mapper_is_allowed(self):
        _, report = self.check(
            "infrastructure/query/DemoOrderQueryMapper.java",
            "package io.github.chrisshi.mom.demo.infrastructure.query; @Mapper interface DemoOrderQueryMapper {}",
        )
        self.assertEqual([], report.errors)

    def test_level1_configuration_is_allowed_when_real_type_exists(self):
        _, report = self.check(
            "infrastructure/configuration/DemoConfiguration.java",
            "package io.github.chrisshi.mom.demo.infrastructure.configuration; @Configuration class DemoConfiguration {}",
        )
        self.assertEqual([], report.errors)

    def test_complex_persistence_entity_is_allowed(self):
        _, report = self.check(
            "infrastructure/persistence/entity/DemoOrderEntity.java",
            "package io.github.chrisshi.mom.demo.infrastructure.persistence.entity; @TableName class DemoOrderEntity {}",
        )
        self.assertEqual([], report.errors)

    def test_complex_persistence_mapper_is_allowed(self):
        _, report = self.check(
            "infrastructure/persistence/mapper/DemoOrderMapper.java",
            "package io.github.chrisshi.mom.demo.infrastructure.persistence.mapper; @Mapper interface DemoOrderMapper extends MomBaseMapper<DemoOrderEntity> {}",
        )
        self.assertEqual([], report.errors)

    def test_level1_feature_subpackage_is_rejected(self):
        _, report = self.check(
            "infrastructure/entity/order/DemoOrderEntity.java",
            "package io.github.chrisshi.mom.demo.infrastructure.entity.order; @TableName class DemoOrderEntity {}",
        )
        self.assertTrue(report.errors)

    def test_entity_in_persistence_feature_package_is_rejected(self):
        _, report = self.check(
            "infrastructure/persistence/order/DemoOrderEntity.java",
            "package io.github.chrisshi.mom.demo.infrastructure.persistence.order; @TableName class DemoOrderEntity {}",
        )
        self.assertTrue(any("Feature-first" in item for item in report.errors))
        self.assertTrue(any("entity" in item for item in report.errors))

    def test_query_mapper_has_dedicated_package(self):
        _, positive = self.check(
            "infrastructure/persistence/query/DemoOrderQueryMapper.java",
            "package io.github.chrisshi.mom.demo.infrastructure.persistence.query; @Mapper interface DemoOrderQueryMapper {}",
        )
        _, negative = self.check(
            "infrastructure/persistence/mapper/DemoOrderQueryMapper.java",
            "package io.github.chrisshi.mom.demo.infrastructure.persistence.mapper; @Mapper interface DemoOrderQueryMapper {}",
        )
        self.assertEqual([], positive.errors)
        self.assertTrue(negative.errors)

    def test_repository_adapter_has_dedicated_package(self):
        _, report = self.check(
            "infrastructure/persistence/repository/MybatisDemoOrderRepository.java",
            "package io.github.chrisshi.mom.demo.infrastructure.persistence.repository; class MybatisDemoOrderRepository {}",
        )
        self.assertEqual([], report.errors)

    def test_unknown_infrastructure_role_is_rejected(self):
        _, report = self.check(
            "infrastructure/dictionary/DictionaryStore.java",
            "package io.github.chrisshi.mom.demo.infrastructure.dictionary; class DictionaryStore {}",
        )
        self.assertTrue(report.errors)

    def test_framework_file_is_outside_bounded_context_scope(self):
        report = module.Report()
        java = module.check_java(
            "mom-framework/mom-data/src/main/java/io/github/chrisshi/mom/data/autoconfigure/DataAutoConfiguration.java",
            "package io.github.chrisshi.mom.data.autoconfigure; class DataAutoConfiguration {}",
            report,
        )
        self.assertIsNone(java)
        self.assertEqual([], report.errors)

    def test_path_and_package_mismatch_is_rejected(self):
        _, report = self.check(
            "infrastructure/entity/DemoOrderEntity.java",
            "package io.github.chrisshi.mom.demo.infrastructure.wrong; class DemoOrderEntity {}",
        )
        self.assertTrue(any("路径期望" in item for item in report.errors))

    def test_comments_strings_and_test_fixture_do_not_trigger(self):
        _, report = self.check(
            "application/DemoText.java",
            'package io.github.chrisshi.mom.demo.application; // @TableName\nclass DemoText { String sample = "class FakeEntity {} @Mapper"; }',
        )
        fixture = module.Report()
        ignored = module.check_java(
            "fixtures/infrastructure/persistence/order/FakeEntity.java",
            "package fixtures.infrastructure.persistence.order; @TableName class FakeEntity {}",
            fixture,
        )
        self.assertEqual([], report.errors)
        self.assertIsNone(ignored)
        self.assertEqual([], fixture.errors)

    def test_xml_namespace_accepts_level1_query_mapper(self):
        java, java_report = self.check(
            "infrastructure/query/DemoOrderQueryMapper.java",
            "package io.github.chrisshi.mom.demo.infrastructure.query; interface DemoOrderQueryMapper {}",
        )
        assert java
        known = {java.fqcn: java}
        positive = module.Report()
        module.check_xml(
            "mom-demo-platform/mom-demo-server/src/main/resources/mapper/demo/DemoOrderQueryMapper.xml",
            f'<mapper namespace="{java.fqcn}"/>',
            known,
            {},
            positive,
        )
        self.assertEqual([], java_report.errors + positive.errors)

    def test_xml_namespace_missing_type_is_rejected(self):
        negative = module.Report()
        module.check_xml(
            "mom-demo-platform/mom-demo-server/src/main/resources/mapper/demo/MissingMapper.xml",
            '<mapper namespace="io.github.chrisshi.mom.demo.infrastructure.query.MissingMapper"/>',
            {},
            {},
            negative,
        )
        self.assertTrue(negative.errors)

    def test_bounded_context_simple_name_conflict_is_review_candidate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            files = [
                self.base + "application/one/SameName.java",
                self.base + "domain/two/SameName.java",
            ]
            for relative in files:
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                package = pathlib.PurePosixPath(relative.removesuffix(".java")).parent.as_posix().split("src/main/java/", 1)[1].replace("/", ".")
                path.write_text(f"package {package}; class SameName {{}}", encoding="utf-8")
            report = module.Report()
            module.run(root, report, files)
            self.assertTrue(any("简单类名" in item for item in report.reviews))


if __name__ == "__main__":
    unittest.main()
