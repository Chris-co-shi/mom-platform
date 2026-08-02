#!/usr/bin/env python3
"""表结构门禁的正例、负例与 Review Candidate 测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest

PATH = pathlib.Path(__file__).with_name("validate_schema_design_baseline.py")
SPEC = importlib.util.spec_from_file_location("schema", PATH)
module = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class SchemaDesignBaselineTest(unittest.TestCase):
    """覆盖命名、约束、类型、注释、跨 Schema 与人工候选。"""

    path = "mom-system-platform/mom-system-server/src/main/resources/db/migration/system/V9__create_item.sql"
    valid = """CREATE TABLE system_item (
      id varchar(19) NOT NULL,
      owner_id varchar(19) NOT NULL,
      status varchar(16) NOT NULL,
      version bigint NOT NULL DEFAULT 0,
      created_at timestamptz NOT NULL,
      CONSTRAINT pk_system_item PRIMARY KEY (id),
      CONSTRAINT uk_system_item_owner UNIQUE (owner_id),
      CONSTRAINT ck_system_item_status CHECK (status IN ('')),
      CONSTRAINT ck_system_item_version_non_negative CHECK (version >= 0)
    );
    CREATE INDEX ix_system_item_status_created ON system_item(status, created_at);
    COMMENT ON TABLE system_item IS 'item';
    COMMENT ON COLUMN system_item.id IS 'id';
    COMMENT ON COLUMN system_item.owner_id IS 'owner';
    COMMENT ON COLUMN system_item.status IS 'status';
    COMMENT ON COLUMN system_item.version IS 'version';"""

    def test_valid_table_passes_and_emits_review(self):
        report = module.Report()
        module.check_sql(self.path, self.valid, report, set())
        self.assertEqual([], report.errors)
        self.assertTrue(report.reviews)

    def test_bad_name_id_time_float_constraint_index_and_comments_fail(self):
        sql = """CREATE TABLE BadTable (
          id bigint PRIMARY KEY,
          amount double precision,
          occurred_at timestamp,
          version bigint,
          code text UNIQUE
        ); CREATE INDEX idx_bad ON BadTable(id);"""
        report = module.Report()
        module.check_sql(self.path, sql, report, set())
        self.assertGreaterEqual(len(report.errors), 7)

    def test_cross_schema_is_rejected(self):
        report = module.Report()
        module.check_sql(self.path, "SELECT id FROM mom_iam.iam_user;", report, set())
        self.assertTrue(report.errors)

    def test_legacy_exception_requires_exact_path_and_table(self):
        report = module.Report()
        module.check_sql(self.path, "CREATE TABLE old_table (id bigint);", report, {(self.path, "old_table")})
        self.assertEqual([], report.errors)
        wrong = module.Report()
        module.check_sql(self.path, "CREATE TABLE old_table (id bigint);", wrong, {(self.path, "other")})
        self.assertTrue(wrong.errors)


if __name__ == "__main__":
    unittest.main()
