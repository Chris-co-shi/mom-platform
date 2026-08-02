#!/usr/bin/env python3
"""持久化门禁的正反例单元测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("validate_persistence_baseline.py")
SPEC = importlib.util.spec_from_file_location("persistence_baseline", MODULE_PATH)
assert SPEC and SPEC.loader
baseline = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = baseline
SPEC.loader.exec_module(baseline)


class PersistenceBaselineTest(unittest.TestCase):
    """覆盖 Flyway、Schema、Mapper XML 及测试资源边界。"""

    def report(self):
        return baseline.Report()

    def test_existing_versioned_migration_modification_is_rejected(self):
        report = self.report()
        path = "mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V1__init.sql"
        baseline.check_versioned_immutability([("M", path)], {path}, report)
        self.assertTrue(report.errors)

    def test_existing_versioned_migration_deletion_is_rejected(self):
        report = self.report()
        path = "mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V1__init.sql"
        baseline.check_versioned_immutability([("D", path)], {path}, report)
        self.assertTrue(report.errors)

    def test_new_valid_migration_is_accepted(self):
        report = self.report()
        path = "mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V2__add_item.sql"
        baseline.check_migration_paths([path], {path: "CREATE TABLE item(id varchar(19));"}, report)
        base = path.replace("V2__add_item.sql", "V1__init.sql")
        baseline.check_versioned_immutability([("A", path)], {base}, report)
        self.assertEqual([], report.errors)

    def test_new_migration_must_exceed_base_version(self):
        report = self.report()
        path = "mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V2__late.sql"
        base = path.replace("V2__late.sql", "V3__existing.sql")
        baseline.check_versioned_immutability([("A", path)], {base}, report)
        self.assertTrue(any("高于 Base 最大版本" in item for item in report.errors))

    def test_duplicate_version_is_rejected(self):
        report = self.report()
        first = "mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V2__add_a.sql"
        second = "mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V2__add_b.sql"
        baseline.check_migration_paths([first, second], {first: "", second: ""}, report)
        self.assertTrue(any("版本重复" in item for item in report.errors))

    def test_wrong_context_path_is_rejected(self):
        report = self.report()
        path = "mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/iam/V1__init.sql"
        baseline.check_migration_paths([path], {path: ""}, report)
        self.assertTrue(any("路径与服务不匹配" in item for item in report.errors))

    def test_cross_schema_sql_is_rejected_and_own_schema_is_allowed(self):
        report = self.report()
        path = "mom-mdm-platform/mom-mdm-server/src/main/resources/db/migration/mdm/V1__init.sql"
        baseline.check_migration_paths([path], {path: "SELECT * FROM mom_iam.iam_user;"}, report)
        self.assertTrue(any("其他 MOM Schema" in item for item in report.errors))
        own = self.report()
        baseline.check_migration_paths([path], {path: "SELECT * FROM mom_mdm.item;"}, own)
        self.assertEqual([], own.errors)

    def test_mapper_dynamic_text_reports_statement_without_sql(self):
        report = self.report()
        xml = '<mapper><select id="find">SELECT id FROM item ORDER BY ${column}</select></mapper>'
        baseline.check_mapper_xml("mapper/TestMapper.xml", xml, report)
        self.assertEqual(["Mapper 动态文本候选: mapper/TestMapper.xml#find"], report.errors)

    def test_test_resource_is_excluded(self):
        path = "mom-mdm-platform/mom-mdm-server/src/test/resources/db/migration/mdm/V100__fixture.sql"
        self.assertIsNone(baseline.migration_details(path))

    def test_obvious_unconditional_update_is_rejected(self):
        report = self.report()
        baseline.check_mapper_xml(
            "mapper/TestMapper.xml", '<mapper><update id="all">UPDATE item SET active=false</update></mapper>', report)
        self.assertTrue(any("显然无条件 UPDATE" in item for item in report.errors))


if __name__ == "__main__":
    unittest.main()
