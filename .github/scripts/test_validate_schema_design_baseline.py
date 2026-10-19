#!/usr/bin/env python3
"""表结构门禁的正例、负例与 Review Candidate 测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest
import tempfile
from unittest.mock import patch

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

    def test_later_migration_comments_can_remediate_existing_table(self):
        create_sql = """CREATE TABLE system_item (
          id varchar(19) NOT NULL,
          version bigint NOT NULL DEFAULT 0,
          CONSTRAINT pk_system_item PRIMARY KEY (id),
          CONSTRAINT ck_system_item_version_non_negative CHECK (version >= 0)
        );"""
        later_comments = create_sql + """
        COMMENT ON TABLE system_item IS 'item';
        COMMENT ON COLUMN system_item.id IS 'id';
        COMMENT ON COLUMN system_item.version IS 'version';"""
        report = module.Report()
        module.check_sql(self.path, create_sql, report, set(), comment_sql=later_comments)
        self.assertEqual([], report.errors)

    def test_new_migration_cannot_borrow_later_comments(self):
        source = self.path
        later = source.replace("V9__create_item.sql", "V10__repair_item_comments.sql")
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for path, sql in ((source, self.valid.split("COMMENT ON TABLE")[0]),
                              (later, self.valid[self.valid.index("COMMENT ON TABLE"):])):
                file = root / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(sql)
            with patch.object(module, "git_files", return_value=[source, later]):
                report = module.Report()
                module.run(root, report)
            self.assertTrue(any("必须有表注释" in error for error in report.errors))

    def test_published_v8_requires_exact_v11_repair(self):
        root_repo = PATH.parents[2]
        source = "mom-wms-platform/mom-wms-server/src/main/resources/db/migration/wms/V8__opening_document_code_rule.sql"
        repair = source.replace("V8__opening_document_code_rule.sql", "V11__repair_code_rule_column_comments.sql")
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for path in (source, repair):
                file = root / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text((root_repo / path).read_text())
            with patch.object(module, "git_files", return_value=[source, repair]):
                report = module.Report()
                module.run(root, report)
                self.assertEqual([], report.errors)
                (root / repair).write_text("-- 修复文件缺少必要 COMMENT\n")
                missing = module.Report()
                module.run(root, missing)
            self.assertTrue(any("关键列必须有注释" in error for error in missing.errors))

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

    def test_published_wms_opening_tables_are_exact_legacy_baseline(self):
        path = "mom-wms-platform/mom-wms-server/src/main/resources/db/migration/wms/V3__opening_inventory.sql"
        for table in ("wms_warehouse_inventory_state", "wms_opening_document", "wms_opening_document_line"):
            with self.subTest(table=table):
                report = module.Report()
                module.check_sql(path, f"CREATE TABLE {table} (id bigint);", report)
                self.assertEqual([], report.errors)
        changed_path = path.replace("V3__opening_inventory.sql", "V4__opening_inventory.sql")
        report = module.Report()
        module.check_sql(changed_path, "CREATE TABLE wms_opening_document (id bigint);", report)
        self.assertTrue(report.errors)

    def repaired_revision_report(self, *, repair=True, source=None, table=None, bad_id=False, rename=None):
        source = source or "mom-qms-platform/mom-qms-server/src/main/resources/db/migration/qms/V1__create_quality_definition.sql"
        table = table or "qms_specification_revision"
        repair_path = "mom-qms-platform/mom-qms-server/src/main/resources/db/migration/qms/V6__repair_schema_metadata.sql"
        sql = self.valid.replace("system_item", table).replace(
            "uk_" + table + "_owner", "uk_qms_specification_revision")
        if bad_id:
            sql = sql.replace("id varchar(19)", "id bigint", 1)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            files = {source: sql}
            if repair:
                files[repair_path] = rename or """ALTER TABLE qms_specification_revision
                    RENAME CONSTRAINT uk_qms_specification_revision TO uk_qms_specification_revision_no;"""
            for name, value in files.items():
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(value)
            report = module.Report()
            with patch.object(module, "git_files", return_value=list(files)):
                module.run(root, report)
            return report

    def test_exact_later_constraint_rename_repairs_historical_name(self):
        self.assertEqual([], self.repaired_revision_report().errors)

    def test_missing_repair_must_fail(self):
        self.assertTrue(self.repaired_revision_report(repair=False).errors)

    def test_commented_wrong_or_partial_rename_must_fail(self):
        for sql in (
            "-- ALTER TABLE qms_specification_revision RENAME CONSTRAINT uk_qms_specification_revision TO uk_qms_specification_revision_no;",
            "ALTER TABLE qms_other RENAME CONSTRAINT uk_qms_specification_revision TO uk_qms_specification_revision_no;",
            "ALTER TABLE qms_specification_revision RENAME CONSTRAINT uk_qms_other TO uk_qms_specification_revision_no;",
            "ALTER TABLE qms_specification_revision RENAME CONSTRAINT uk_qms_specification_revision TO uk_qms_specification_revision_wrong;",
        ):
            with self.subTest(sql=sql):
                self.assertTrue(self.repaired_revision_report(rename=sql).errors)

    def test_rename_does_not_exempt_types_other_files_or_tables(self):
        self.assertTrue(self.repaired_revision_report(bad_id=True).errors)
        self.assertTrue(self.repaired_revision_report(source=self.path.replace("system/", "qms/")).errors)
        self.assertTrue(self.repaired_revision_report(table="qms_other").errors)


if __name__ == "__main__":
    unittest.main()
