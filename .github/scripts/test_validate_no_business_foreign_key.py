#!/usr/bin/env python3
"""无物理外键门禁的正例、负例与精确例外测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest

PATH = pathlib.Path(__file__).with_name("validate_no_business_foreign_key.py")
SPEC = importlib.util.spec_from_file_location("no_fk", PATH)
module = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class NoBusinessForeignKeyTest(unittest.TestCase):
    """覆盖正常 DDL、四类禁用语法、大小写、注释/字符串与官方表例外。"""

    path = "mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V9__fixture.sql"

    def test_plain_table_is_allowed(self):
        report = module.Report()
        module.check_sql(self.path, "CREATE TABLE child (id varchar(19));", report, set(), set())
        self.assertEqual([], report.errors)

    def test_foreign_key_and_references_are_rejected_case_insensitively(self):
        for sql in (
            "CREATE TABLE child (parent_id varchar(19), FOREIGN KEY(parent_id) REFERENCES parent(id));",
            "create table child (parent_id varchar(19) ReFeReNcEs parent(id));",
            "CREATE TABLE child (parent_id varchar(19), FOREIGN KEY(parent_id) REFERENCES parent(id) ON DELETE CASCADE);",
            "CREATE TABLE child (parent_id varchar(19), FOREIGN KEY(parent_id) REFERENCES parent(id) ON UPDATE CASCADE);",
        ):
            report = module.Report()
            module.check_sql(self.path, sql, report, set(), set())
            self.assertTrue(report.errors, sql)

    def test_comments_and_strings_do_not_trigger(self):
        report = module.Report()
        sql = """-- FOREIGN KEY x REFERENCES y(id)
        CREATE TABLE child (id varchar(19), note text DEFAULT 'ON DELETE CASCADE');
        /* REFERENCES ignored(id) */"""
        module.check_sql(self.path, sql, report, set(), set())
        self.assertEqual([], report.errors)

    def test_official_protocol_exception_is_exact(self):
        official = "mom-iam-platform/mom-iam-server/src/main/resources/db/migration/iam/V99__official.sql"
        sql = "CREATE TABLE oauth2_official (client_id varchar(100) REFERENCES oauth2_client(id));"
        report = module.Report()
        module.check_sql(official, sql, report, {(official, "oauth2_official")}, set())
        self.assertEqual([], report.errors)
        wrong_table = module.Report()
        module.check_sql(official, sql, wrong_table, {(official, "other")}, set())
        self.assertTrue(wrong_table.errors)

    def test_historical_source_requires_exact_removal_resolution(self):
        historical = {(self.path, "child")}
        sql = "CREATE TABLE child (parent_id varchar(19) REFERENCES parent(id));"
        unresolved = module.Report()
        module.check_sql(self.path, sql, unresolved, set(), historical)
        self.assertTrue(unresolved.errors)

        resolved = module.Report()
        module.check_sql(self.path, sql, resolved, set(), historical, historical)
        self.assertEqual([], resolved.errors)
        self.assertIn("已由指定更高版本删除", resolved.reviews[0])


if __name__ == "__main__":
    unittest.main()
