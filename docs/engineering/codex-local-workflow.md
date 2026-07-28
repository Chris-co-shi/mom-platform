# MOM Platform 本地 Codex 工作流

## 目标

本工作流用于让 Codex 在本地修改 MOM Platform 时保持验证范围可控，并避免把完整 Maven、Surefire、Failsafe 或中间件日志放入模型上下文。

## 固定入口

### 环境检查

```bash
bash scripts/codex-doctor.sh
```

该命令只检查 JDK、Maven、Python、Git、Docker 可用性和工程基线，不执行 Maven，也不启动 Nacos、Redis、PostgreSQL、Seata 或 RocketMQ。

### 变更范围验证

```bash
bash scripts/codex-verify-changed.sh
```

默认行为：

1. 合并 `origin/main...HEAD`、暂存区、工作区和未跟踪文件的变更列表；
2. 执行官方工程基线静态检查；
3. 将 Java/Maven 变更映射到最近的 Maven 模块；
4. 使用 `-pl ... -am` 只测试相关模块；
5. 文档或非 Maven 文件变更不执行 Maven。

可选级别：

```bash
CODEX_VERIFY_LEVEL=compile bash scripts/codex-verify-changed.sh
CODEX_VERIFY_LEVEL=test bash scripts/codex-verify-changed.sh
CODEX_VERIFY_LEVEL=verify bash scripts/codex-verify-changed.sh
```

定向测试：

```bash
CODEX_TEST='SomeTest#someMethod' \
  bash scripts/codex-verify-changed.sh
```

### 直接 Maven 包装入口

必须使用：

```bash
bash scripts/codex-mvn-test.sh -pl <module> -am test
```

不得让 Codex 直接运行带完整终端输出的 `mvn test` 或 `mvn clean verify`。

## 日志策略

完整日志保存在：

```text
.codex/runtime/logs/
```

有界摘要保存在：

```text
.codex/runtime/summaries/
```

失败时按以下顺序读取：

1. 摘要；
2. 对应 Surefire/Failsafe XML 或文本报告；
3. 失败测试和相关生产源码；
4. 摘要不足时，按异常名读取完整日志的局部范围。

禁止把完整 Reactor 日志一次性送入模型。

## 中间件边界

普通单元测试不启动 Nacos、Redis、PostgreSQL、Seata、RocketMQ 或可观测性栈。

本地需要真实基础设施时，先确认变更确实命中对应能力，再调用专用 Smoke 脚本。已经健康的容器应复用；只有镜像、Compose、初始化脚本变化、容器异常或测试明确要求全新环境时才重建。

## 最终门禁

局部实现阶段使用变更模块测试；Slice、Phase、发布或公共依赖变更完成时才执行：

```bash
bash scripts/codex-mvn-test.sh clean verify
```
