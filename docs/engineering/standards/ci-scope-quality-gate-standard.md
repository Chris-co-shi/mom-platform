# CI 范围检测与质量门禁规范

- 状态：Current
- 生效 Slice：P1.6 S04

## 1. Scope 输出

统一脚本输出 `nacos`、`redis_cache`、`redis_idempotency`、`redis_rate_limit`、`postgresql`、`messaging`。主 CI 的手动范围支持 `none/all/nacos/redis/postgresql/messaging`；`redis` 同时选择三个 Redis 证据，不重新合并 Job。

## 2. Git 范围

- PR 首次创建、Reopen、Base 变化、旧 Head 不可达或不为新 Head ancestor：Base → Head 全差异。
- 长期 PR synchronize 且 Previous Head 是新 Head ancestor：Previous Head → New Head。
- main push：before → after；before 缺失/不可达时安全回退完整历史根 → after。
- Manual `all`：全部基础设施范围。

Scope Detector 或主 CI 自身修改触发主 CI 全部基础设施分支。文档与静态门禁脚本不因提及中间件而误触发运行时验收。

## 3. 必需门禁

每次 PR：Engineering Baseline、Fast Reactor Verify、ArchUnit、Secure Defaults、Persistence、Runtime Security、Test Baseline。条件门禁：PostgreSQL、Nacos Discovery、三个 Redis 与 Messaging Runtime Smoke。`skipped` 与 `success` 分开记录。可观测性配置资产由 Engineering Baseline 静态检查；Seata 当前没有生产调用方，不设置仅编译模块却宣称真实 AT 的独立门禁。未来采用 Seata 或恢复可观测性端到端结论前，必须先按相应 ADR 恢复真实基础设施验收。

## 4. Job 结构

基础设施 Job 均设置小于 Workflow 总预算的 timeout、独立 Artifact 和 cleanup；三个 Nacos/Redis Job 只依赖 scope 与 Fast Reactor，不相互串行。缓存只用于 Maven 依赖，不缓存运行时正确性状态。权限保持 `contents: read`。使用 Previous Head 增量范围的基础设施 Workflow 必须保留已开始的执行（`cancel-in-progress: false`），防止新 Push 取消尚未产生证据的范围；只有不承载增量基础设施证据的快速静态 Workflow 可取消过期运行。

## 5. Codex 与日志

Maven 必须通过包装脚本；完整日志进入 `.codex/runtime/logs`，失败摘要进入 `.codex/runtime/summaries`。Artifact 只上传有界诊断和测试报告，不上传 Secret。CI 禁止 `maven.test.skip=true`，JDK 固定 25。
