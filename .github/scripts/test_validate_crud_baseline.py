#!/usr/bin/env python3
"""CRUD 与多表查询门禁的正反例测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest

PATH = pathlib.Path(__file__).with_name("validate_crud_baseline.py")
SPEC = importlib.util.spec_from_file_location("crud", PATH)
module = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class CrudBaselineTest(unittest.TestCase):
    """覆盖默认 Mapper、分层、Entity、重复 CRUD、Projection 与查询候选。"""

    root = "mom-system-platform/mom-system-server/src/main/java/io/github/chrisshi/mom/system/"

    def check(self, suffix: str, text: str):
        report = module.Report()
        module.check_java_file(self.root + suffix, text, report)
        return report

    def test_mom_base_mapper_is_allowed(self):
        report = self.check("infrastructure/persistence/item/ItemMapper.java", "interface ItemMapper extends MomBaseMapper<ItemEntity> {}")
        self.assertEqual([], report.errors)

    def test_plain_mapper_and_duplicate_crud_are_rejected(self):
        report = self.check("infrastructure/persistence/item/ItemMapper.java", "interface ItemMapper { ItemEntity selectById(String id); }")
        self.assertTrue(any("MomBaseMapper" in item for item in report.errors))
        self.assertTrue(any("重复声明" in item for item in report.errors))

    def test_controller_repository_dependency_is_rejected(self):
        report = self.check("web/item/ItemController.java", "import a.b.ItemRepository; class ItemController {}")
        self.assertTrue(report.errors)

    def test_application_mapper_dependency_is_rejected(self):
        report = self.check("application/item/ItemService.java", "import a.b.ItemMapper; class ItemService {}")
        self.assertTrue(report.errors)

    def test_domain_mybatis_dependency_is_rejected(self):
        report = self.check("domain/item/Item.java", "import com.baomidou.mybatisplus.core.conditions.Wrapper; class Item {}")
        self.assertTrue(report.errors)

    def test_entity_data_is_rejected(self):
        report = self.check("infrastructure/persistence/item/ItemEntity.java", "@Data\nclass ItemEntity {}")
        self.assertTrue(report.errors)

    def test_query_mapper_must_use_query_package_and_projection(self):
        report = self.check("infrastructure/persistence/item/ItemQueryMapper.java", "interface ItemQueryMapper { List<ItemEntity> find(); }")
        self.assertGreaterEqual(len(report.errors), 2)

    def test_join_pagination_and_unbounded_in_are_review_candidates(self):
        report = module.Report()
        xml = '<mapper><select id="list">SELECT p.id, c.id FROM parent p JOIN child c ON c.parent_id=p.id LIMIT #{size}<foreach collection="ids" item="id" open=" AND p.id IN (" close=")">#{id}</foreach></select></mapper>'
        module.check_mapper_xml("mapper/ParentQueryMapper.xml", xml, report)
        self.assertEqual([], report.errors)
        self.assertEqual(2, len(report.reviews))

    def test_select_star_and_dynamic_sort_are_rejected(self):
        report = module.Report()
        module.check_mapper_xml("mapper/Q.xml", '<mapper><select id="x">SELECT * FROM t ORDER BY ${sort}</select></mapper>', report)
        self.assertEqual(2, len(report.errors))


if __name__ == "__main__":
    unittest.main()
