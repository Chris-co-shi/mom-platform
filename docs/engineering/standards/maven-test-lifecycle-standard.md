# Maven 测试生命周期规范

- 状态：Current
- 生效 Slice：P1.6 S04

## 1. Surefire

根 POM 统一管理 Maven Surefire `3.5.4`，显式包含 `**/*Test.java`、`**/*Tests.java`，排除 `**/*IT.java`、`**/*ITCase.java`。Surefire 承载 L1/L2；不得启动 Nacos、Seata或要求本机 PostgreSQL/Redis/RocketMQ。

## 2. Failsafe

根 POM 统一管理 Maven Failsafe `3.5.4`，包含 `**/*IT.java`、`**/*ITCase.java`，并同时绑定 `integration-test`、`verify`。只有包含匹配类的模块实际运行 IT；失败由 `verify` 判定构建失败。不得只执行 `integration-test` 后结束。

`-DskipTests` 跳过执行但仍允许测试编译；`maven.test.skip=true` 会隐藏测试编译并禁止进入 CI。普通 `test` 不执行 Failsafe，完整验收使用 `verify`。

## 3. JUnit 与进程

JUnit Jupiter 使用 JUnit Platform。默认 per-method 实例和顺序执行；不全局开启并行。根配置不设置 `forkCount=0`、无限并行或通过 `argLine` 绕过 JDK 25 封装。

## 4. 本地与 Codex

使用 `scripts/codex-mvn-test.sh` 保存完整日志并只输出有界摘要。顺序为：test-compile、测试类、模块 test/verify、`codex-verify-changed.sh`、Slice 最终 `clean verify`。系统 Maven 不满足 3.9.9 时显式设置 `MAVEN_BIN`，不得降低 Enforcer。

## 5. 当前迁移

5 个 Testcontainers 测试由 `*IntegrationTest` 行为保持改名为 `*IT`；快速 Resource Server Context 测试改名为 `*Test`。未修改断言，也未删除测试。

## 官方依据

- [Surefire 默认命名](https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html)
- [Failsafe 默认命名](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/inclusion-exclusion.html)
- [Failsafe Verify](https://maven.apache.org/surefire/maven-failsafe-plugin/verify-mojo.html)
