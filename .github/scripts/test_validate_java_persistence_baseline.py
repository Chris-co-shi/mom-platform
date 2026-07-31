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
            "io/github/chrisshi/mom/system/infrastructure/persistence/repository/JdbcRepository.java"
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
            "io/github/chrisshi/mom/system/infrastructure/persistence/mapper/NewMapper.java"
        )
        text = 'import org.apache.ibatis.annotations.Select;\n@Select("SELECT * FROM system_parameter")'
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("SELECT *" in item for item in report.errors))

    def test_explicit_columns_are_allowed(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/mapper/SystemParameterMapper.java"
        )
        text = 'import org.apache.ibatis.annotations.Select;\n@Select("SELECT id FROM system_parameter")'
        baseline.check_java_file(path, text, report)
        self.assertEqual([], report.errors)

    def test_mybatis_dynamic_text_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/mapper/NewMapper.java"
        )
        text = 'import org.apache.ibatis.annotations.Select;\n@Select("SELECT id ORDER BY ${column}")'
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("动态文本" in item for item in report.errors))

    def test_iservice_is_rejected_everywhere(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/application/parameter/BadService.java"
        )
        text = "import com.baomidou.mybatisplus.extension.service.IService;"
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("IService/ServiceImpl" in item for item in report.errors))

    def test_crud_repository_outside_infrastructure_repository_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/application/parameter/BadRepository.java"
        )
        text = (
            "import com.baomidou.mybatisplus.extension.repository.CrudRepository;\n"
            "class BadRepository extends CrudRepository<BadMapper, BadEntity> {}"
        )
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("只能位于 Infrastructure Repository" in item for item in report.errors))

    def test_explicit_irepository_contract_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/domain/parameter/BadRepository.java"
        )
        text = (
            "import com.baomidou.mybatisplus.extension.repository.IRepository;\n"
            "interface BadRepository extends IRepository<BadEntity> {}"
        )
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("不得显式继承或实现 IRepository" in item for item in report.errors))

    def test_required_single_table_adapter_must_extend_crud_repository(self):
        report = self.report()
        path = next(iter(baseline.REQUIRED_CRUD_REPOSITORIES))
        text = "class MybatisSystemParameterRepository implements SystemParameterRepository {}"
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("必须复用 CrudRepository" in item for item in report.errors))

    def test_valid_single_table_adapter_is_allowed(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/repository/"
            "MybatisSystemParameterRepository.java"
        )
        text = (
            "import com.baomidou.mybatisplus.extension.repository.CrudRepository;\n"
            "class MybatisSystemParameterRepository "
            "extends CrudRepository<SystemParameterMapper, SystemParameterEntity> "
            "implements SystemParameterRepository {}"
        )
        baseline.check_java_file(path, text, report)
        self.assertEqual([], report.errors)

    def test_multi_mapper_repository_is_not_mechanically_required(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/repository/"
            "MybatisSystemI18nRepository.java"
        )
        text = "class MybatisSystemI18nRepository implements SystemI18nRepository {}"
        baseline.check_java_file(path, text, report)
        self.assertEqual([], report.errors)

    def test_upper_layer_cannot_import_concrete_mybatis_adapter(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/application/parameter/BadApplicationService.java"
        )
        text = (
            "import io.github.chrisshi.mom.system.infrastructure.persistence.repository."
            "MybatisSystemParameterRepository;"
        )
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("不得依赖具体 MyBatis Repository Adapter" in item for item in report.errors))


if __name__ == "__main__":
    unittest.main()
