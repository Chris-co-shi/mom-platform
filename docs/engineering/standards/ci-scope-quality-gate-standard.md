# CI 范围检测与质量门禁规范

- 状态：Current
- 生效 Slice：P1.6 S04

## 1. Scope 输出

统一脚本输出 `nacos`、`redis_idempotency`、`redis_rate_limit`、`postgresql`、`messaging`、`seata`、`observability`。主 CI 的手动范围支持 `none/all/nacos/redis/postgresql/seata/messaging/observability`；`redis` 同时选择两个 Redis 证据，不重新合并 Job。

## 2. Git 范围

- PR 首次创建、Reopen、Base 变化、旧 Head 不可达或不为新 Head ancestor：Base → Head 全差异。
- 长期 PR synchronize 且 Previous Head 是新 Head ancestor：Previous Head → New Head。
- main push：before → after；before 缺失/不可达时安全回退完整历史根 → after。
- Manual `all`：全部基础设施范围。

Scope Detector 或主 CI 自身修改触发主 CI 全部基础设施分支；各专项 Workflow 自身修改触发自身范围。文档与静态门禁脚本不因提及中间件而误触发运行时验收。

## 3. 必需门禁

每次 PR：Engineering Baseline、Fast Reactor Verify、ArchUnit、Secure Defaults、Persistence、Runtime Security、Test Baseline。条件门禁：PostgreSQL、Nacos Discovery、两个 Redis、Messaging、Seata、Observability 与 Stack。`skipped` 与 `success` 分开记录。

## 4. Job 结构

基础设施 Job 均设置小于 Workflow 总预算的 timeout、独立 Artifact 和 cleanup；三个 Nacos/Redis Job只依赖 scope 与 Fast Reactor，不相互串行。缓存只用于 Maven 依赖，不缓存运行时正确性状态。权限保持 `contents: read`，并使用 concurrency 取消同引用的过期执行。

## 5. Codex 与日志

Maven 必须通过包装脚本；完整日志进入 `.codex/runtime/logs`，失败摘要进入 `.codex/runtime/summaries`。Artifact 只上传有界诊断和测试报告，不上传 Secret。CI 禁止 `maven.test.skip=true`，JDK 固定 25。
