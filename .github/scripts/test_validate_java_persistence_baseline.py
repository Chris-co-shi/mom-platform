#!/usr/bin/env python3
"""validate_java_persistence_baseline.py 的正反例测试。"""

from __future__ import annotations

import importlib.util
import pathlib
import sys
import unittest

SCRIPT = pathlib.Path(__file__).with_name(
    "validate_java_persistence_baseline.py"
)
SPEC = importlib.util.spec_from_file_location(
    "java_persistence_baseline", SCRIPT
)
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
            "io/github/chrisshi/mom/system/infrastructure/persistence/"
            "repository/JdbcRepository.java"
        )
        baseline.check_java_file(
            path,
            "import org.springframework.jdbc.core.JdbcTemplate;",
            report,
        )
        self.assertTrue(
            any("禁止直接 JDBC" in item for item in report.errors)
        )

    def test_precise_sas_jdbc_exception_is_allowed(self):
        report = self.report()
        path = next(
            item
            for item in baseline.DIRECT_JDBC_EXCEPTIONS
            if "IamAuthorization" in item
        )
        baseline.check_java_file(
            path,
            "import org.springframework.jdbc.core.JdbcTemplate;",
            report,
        )
        self.assertEqual([], report.errors)

    def test_select_star_in_new_mapper_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/infrastructure/persistence/"
            "mapper/NewMapper.java"
        )
        text = (
            'import org.apache.ibatis.annotations.Select;\n'
            '@Select("SELECT * FROM system_parameter")'
        )
        baseline.check_java_file(path, text, report)
        self.assertTrue(any("SELECT *" in item for item in report.errors))

    def test_current_and_legacy_iservice_are_rejected(self):
        for package in (
            "com.baomidou.mybatisplus.spring.service.IService",
            "com.baomidou.mybatisplus.extension.service.IService",
        ):
            report = self.report()
            path = (
                "mom-system-platform/mom-system-server/src/main/java/"
                "io/github/chrisshi/mom/system/application/BadService.java"
            )
            baseline.check_java_file(
                path, f"import {package};", report
            )
            self.assertTrue(
                any("IService/ServiceImpl" in item for item in report.errors)
            )

    def test_repository_outside_infrastructure_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/application/BadRepository.java"
        )
        text = (
            "import com.baomidou.mybatisplus.spring.repository.CrudRepository;\n"
            "class BadRepository extends CrudRepository<M,E> {}"
        )
        baseline.check_java_file(path, text, report)
        self.assertTrue(
            any(
                "只能位于 Infrastructure Repository" in item
                for item in report.errors
            )
        )

    def test_explicit_irepository_contract_is_rejected(self):
        report = self.report()
        path = (
            "mom-system-platform/mom-system-server/src/main/java/"
            "io/github/chrisshi/mom/system/domain/BadRepository.java"
        )
        text = (
            "import com.baomidou.mybatisplus.extension.repository.IRepository;\n"
            "interface BadRepository extends IRepository<E> {}"
        )
        baseline.check_java_file(path, text, report)
        self.assertTrue(
            any(
                "不得显式继承或实现 IRepository" in item
                for item in report.errors
            )
        )

    def test_every_required_single_table_adapter_uses_crud_repository(self):
        for path in baseline.REQUIRED_CRUD_REPOSITORIES:
            report = self.report()
            name = pathlib.PurePosixPath(path).stem
            baseline.check_java_file(
                path,
                f"class {name} implements DomainRepository {{}}",
                report,
            )
            self.assertTrue(
                any(
                    "必须复用 CrudRepository" in item
                    for item in report.errors
                ),
                path,
            )

    def test_iam_user_and_role_adapters_are_governed(self):
        required = {
            path
            for path in baseline.REQUIRED_CRUD_REPOSITORIES
            if "mom-iam-platform" in path
        }
        self.assertEqual(
            {
                "MybatisIamUserAccountRepository.java",
                "MybatisIamRoleRepository.java",
            },
            {pathlib.PurePosixPath(path).name for path in required},
        )

    def test_valid_single_table_adapter_is_allowed(self):
        report = self.report()
        path = (
            "mom-iam-platform/mom-iam-server/src/main/java/"
            "io/github/chrisshi/mom/iam/infrastructure/persistence/"
            "repository/MybatisIamUserAccountRepository.java"
        )
        text = (
            "import com.baomidou.mybatisplus.spring.repository.CrudRepository;\n"
            "class MybatisIamUserAccountRepository "
            "extends CrudRepository<IamUserMapper, IamUserEntity> "
            "implements IamUserAccountRepository, IamUserAdminQueryPort {}"
        )
        baseline.check_java_file(path, text, report)
        self.assertEqual([], report.errors)

    def test_multi_mapper_repository_is_not_mechanically_required(self):
        report = self.report()
        path = (
            "mom-iam-platform/mom-iam-server/src/main/java/"
            "io/github/chrisshi/mom/iam/infrastructure/persistence/"
            "repository/MybatisIamAuthorizationAssignmentRepository.java"
        )
        text = (
            "class MybatisIamAuthorizationAssignmentRepository "
            "implements IamAuthorizationAssignmentPort {}"
        )
        baseline.check_java_file(path, text, report)
        self.assertEqual([], report.errors)

    def test_upper_layer_cannot_import_concrete_adapter(self):
        report = self.report()
        path = (
            "mom-iam-platform/mom-iam-server/src/main/java/"
            "io/github/chrisshi/mom/iam/application/admin/BadService.java"
        )
        text = (
            "import io.github.chrisshi.mom.iam.infrastructure.persistence."
            "repository.MybatisIamUserAccountRepository;"
        )
        baseline.check_java_file(path, text, report)
        self.assertTrue(
            any(
                "不得依赖具体 MyBatis Repository Adapter" in item
                for item in report.errors
            )
        )


if __name__ == "__main__":
    unittest.main()
