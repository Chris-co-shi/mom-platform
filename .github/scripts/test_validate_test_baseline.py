#!/usr/bin/env python3
"""测试测试基线门禁与范围检测的正反例。"""

from __future__ import annotations

import importlib.util
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest


SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "validate_test_baseline", SCRIPT_DIR / "validate_test_baseline.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


VALID_POM = """\
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <properties>
    <maven-surefire-plugin.version>3.5.4</maven-surefire-plugin.version>
    <maven-failsafe-plugin.version>3.5.4</maven-failsafe-plugin.version>
  </properties>
  <build>
    <pluginManagement><plugins>
      <plugin><artifactId>maven-surefire-plugin</artifactId><configuration>
        <includes><include>**/*Test.java</include><include>**/*Tests.java</include></includes>
        <excludes><exclude>**/*IT.java</exclude><exclude>**/*ITCase.java</exclude></excludes>
      </configuration></plugin>
      <plugin><artifactId>maven-failsafe-plugin</artifactId><configuration>
        <includes><include>**/*IT.java</include><include>**/*ITCase.java</include></includes>
      </configuration></plugin>
    </plugins></pluginManagement>
    <plugins><plugin><artifactId>maven-failsafe-plugin</artifactId><executions><execution><goals>
      <goal>integration-test</goal><goal>verify</goal>
    </goals></execution></executions></plugin></plugins>
  </build>
</project>
"""


VALID_WORKFLOW = """\
name: Test
concurrency:
  group: test-${{ github.ref }}
  cancel-in-progress: false
jobs:
  redis-idempotency-smoke:
    if: needs.changes.outputs.redis_idempotency == 'true'
    needs: [changes, verify]
    timeout-minutes: 10
    steps:
      - uses: actions/setup-java@v4
        with:
          java-version: "25"
      - if: failure()
        uses: actions/upload-artifact@v4
      - run: bash .github/scripts/detect-ci-scope.sh
"""


class BaselineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)
        (self.root / ".github/workflows").mkdir(parents=True)
        (self.root / ".github/scripts").mkdir(parents=True)
        (self.root / "pom.xml").write_text(VALID_POM, encoding="utf-8")
        (self.root / ".github/workflows/ci.yml").write_text(VALID_WORKFLOW, encoding="utf-8")
        detector = "\n".join(f"emit {name} false" for name in (
            "nacos", "redis_cache", "redis_idempotency", "redis_rate_limit", "postgresql",
            "messaging", "seata", "observability",
        ))
        (self.root / ".github/scripts/detect-ci-scope.sh").write_text(detector, encoding="utf-8")
        for name in (
            "nacos-discovery-smoke.sh", "redis-idempotency-smoke.sh", "redis-rate-limit-smoke.sh"
        ):
            (self.root / ".github/scripts" / name).write_text(
                "#!/usr/bin/env bash\nset -Eeuo pipefail\ncleanup() { :; }\ntrap cleanup EXIT\n",
                encoding="utf-8",
            )

    def tearDown(self) -> None:
        self.temp.cleanup()

    def rules(self) -> set[str]:
        return {item.rule for item in MODULE.validate(self.root)}

    def test_valid_fixture_passes(self) -> None:
        self.assertEqual([], MODULE.validate(self.root))

    def test_rejects_unmanaged_plugin_version(self) -> None:
        (self.root / "pom.xml").write_text(VALID_POM.replace("3.5.4", "3.5.3", 1), encoding="utf-8")
        self.assertIn("TB001", self.rules())

    def test_rejects_missing_failsafe_verify(self) -> None:
        (self.root / "pom.xml").write_text(VALID_POM.replace("<goal>verify</goal>", ""), encoding="utf-8")
        self.assertIn("TB003", self.rules())

    def test_rejects_testcontainers_in_surefire_name(self) -> None:
        path = self.root / "module/src/test/java/RedisContainerTest.java"
        path.parent.mkdir(parents=True)
        path.write_text("import org.testcontainers.junit.jupiter.Testcontainers; @Testcontainers class RedisContainerTest {}", encoding="utf-8")
        self.assertIn("TB004", self.rules())

    def test_accepts_testcontainers_it_name(self) -> None:
        path = self.root / "module/src/test/java/RedisContainerIT.java"
        path.parent.mkdir(parents=True)
        path.write_text("import org.testcontainers.junit.jupiter.Testcontainers; @Testcontainers class RedisContainerIT {}", encoding="utf-8")
        self.assertEqual([], MODULE.validate(self.root))

    def test_rejects_thread_sleep(self) -> None:
        path = self.root / "module/src/test/java/SlowTest.java"
        path.parent.mkdir(parents=True)
        path.write_text("class SlowTest { void waitForIt() throws Exception { Thread.sleep(10000); } }", encoding="utf-8")
        self.assertIn("TB005", self.rules())

    def test_rejects_latest_container_image(self) -> None:
        path = self.root / "module/src/test/java/RedisIT.java"
        path.parent.mkdir(parents=True)
        path.write_text('class RedisIT { Object value = DockerImageName.parse("redis:latest"); }', encoding="utf-8")
        self.assertIn("TB006", self.rules())

    def test_rejects_job_without_timeout(self) -> None:
        workflow = VALID_WORKFLOW.replace("    timeout-minutes: 10\n", "")
        (self.root / ".github/workflows/ci.yml").write_text(workflow, encoding="utf-8")
        self.assertIn("TB007", self.rules())

    def test_rejects_smoke_without_failure_artifact(self) -> None:
        workflow = VALID_WORKFLOW.replace("      - if: failure()\n        uses: actions/upload-artifact@v4\n", "")
        (self.root / ".github/workflows/ci.yml").write_text(workflow, encoding="utf-8")
        self.assertIn("TB008", self.rules())

    def test_rejects_combined_nacos_redis_job(self) -> None:
        workflow = VALID_WORKFLOW.replace("redis-idempotency-smoke", "nacos-redis-smoke")
        (self.root / ".github/workflows/ci.yml").write_text(workflow, encoding="utf-8")
        self.assertIn("TB009", self.rules())

    def test_rejects_redis_dependency_on_nacos(self) -> None:
        workflow = VALID_WORKFLOW.replace("[changes, verify]", "[changes, nacos-discovery-smoke]")
        (self.root / ".github/workflows/ci.yml").write_text(workflow, encoding="utf-8")
        self.assertIn("TB010", self.rules())

    def test_rejects_non_strict_smoke_script(self) -> None:
        (self.root / ".github/scripts/redis-rate-limit-smoke.sh").write_text("#!/bin/bash\n", encoding="utf-8")
        self.assertIn("TB011", self.rules())

    def test_rejects_maven_test_skip(self) -> None:
        (self.root / ".github/workflows/ci.yml").write_text(VALID_WORKFLOW + "# maven.test.skip=true\n", encoding="utf-8")
        self.assertIn("TB012", self.rules())

    def test_rejects_java_version_drift(self) -> None:
        (self.root / ".github/workflows/ci.yml").write_text(VALID_WORKFLOW.replace('"25"', '"21"'), encoding="utf-8")
        self.assertIn("TB013", self.rules())

    def test_rejects_missing_scope_output(self) -> None:
        path = self.root / ".github/scripts/detect-ci-scope.sh"
        path.write_text(path.read_text(encoding="utf-8").replace("emit nacos false\n", ""), encoding="utf-8")
        self.assertIn("TB014", self.rules())

    def test_rejects_cancelling_incremental_infrastructure_evidence(self) -> None:
        workflow = VALID_WORKFLOW.replace("cancel-in-progress: false", "cancel-in-progress: true")
        (self.root / ".github/workflows/ci.yml").write_text(workflow, encoding="utf-8")
        self.assertIn("TB015", self.rules())


class ScopeDetectorTest(unittest.TestCase):
    def run_detector(self, root: pathlib.Path, **environment: str) -> dict[str, str]:
        output = root / "outputs.txt"
        summary = root / "summary.txt"
        env = os.environ.copy()
        env.update(environment, GITHUB_OUTPUT=str(output), GITHUB_STEP_SUMMARY=str(summary))
        subprocess.run(
            ["bash", ".github/scripts/detect-ci-scope.sh"], cwd=root,
            env=env, check=True, capture_output=True, text=True,
        )
        return dict(line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines())

    def test_manual_redis_is_independent_from_nacos(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / ".github/scripts").mkdir(parents=True)
            shutil.copy2(SCRIPT_DIR / "detect-ci-scope.sh", root / ".github/scripts/detect-ci-scope.sh")
            result = self.run_detector(root, MANUAL_SCOPE="redis")
            self.assertEqual("false", result["nacos"])
            self.assertEqual("true", result["redis_cache"])
            self.assertEqual("true", result["redis_idempotency"])
            self.assertEqual("true", result["redis_rate_limit"])

    def test_incremental_idempotency_scope(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "S04 Test"], cwd=root, check=True)
            (root / ".github/scripts").mkdir(parents=True)
            shutil.copy2(SCRIPT_DIR / "detect-ci-scope.sh", root / ".github/scripts/detect-ci-scope.sh")
            (root / "README.md").write_text("base\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=root, check=True)
            base = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
            path = root / "mom-framework/mom-idempotency/src/main/java/Example.java"
            path.parent.mkdir(parents=True)
            path.write_text("class Example {}\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "head"], cwd=root, check=True)
            head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
            result = self.run_detector(
                root, MANUAL_SCOPE="auto", EVENT_NAME="pull_request", PR_ACTION="synchronize",
                PR_PREVIOUS_SHA=base, PR_BASE_SHA=base, PR_HEAD_SHA=head,
            )
            self.assertEqual("auto:pull-request-incremental", result["mode"])
            self.assertEqual("true", result["redis_idempotency"])
            self.assertEqual("false", result["nacos"])

    def test_incremental_cache_scope(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=root, check=True)
            subprocess.run(["git", "config", "user.name", "Cache Test"], cwd=root, check=True)
            (root / ".github/scripts").mkdir(parents=True)
            shutil.copy2(SCRIPT_DIR / "detect-ci-scope.sh", root / ".github/scripts/detect-ci-scope.sh")
            (root / "README.md").write_text("base\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=root, check=True)
            base = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
            path = root / "mom-framework/mom-cache/src/main/java/Example.java"
            path.parent.mkdir(parents=True)
            path.write_text("class Example {}\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=root, check=True)
            subprocess.run(["git", "commit", "-qm", "head"], cwd=root, check=True)
            head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
            result = self.run_detector(
                root, MANUAL_SCOPE="auto", EVENT_NAME="pull_request", PR_ACTION="synchronize",
                PR_PREVIOUS_SHA=base, PR_BASE_SHA=base, PR_HEAD_SHA=head,
            )
            self.assertEqual("true", result["redis_cache"])
            self.assertEqual("false", result["redis_idempotency"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
