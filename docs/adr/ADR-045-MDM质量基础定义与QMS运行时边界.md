# ADR-045：MDM 质量基础定义与 QMS 运行时边界

- 状态：Accepted
- 日期：2026-10-15
- 关联：ADR-025、ADR-044

## 背景

检验特性、检验方法和质量标准最初实现在 QMS，但它们描述的是被多个业务流程引用、具有版本生命周期的静态定义。检验执行、结果、判定与放行则是生产和库存过程中的运行时事实。把两类数据放在同一所有权中，会让 MDM 一致性治理和 QMS 运行时事务边界混淆。

## 决策

- MDM 拥有 Inspection Characteristic、Inspection Method、Quality Specification、Revision 和 Item。
- 上述数据存储于 `mom_mdm`，由 MDM Controller/Application/Flyway 管理。
- 管理权限使用 `mdm:quality-definition:read` 与 `mdm:quality-definition:write`。
- Web 路由、导航和动态文案归入 MDM，国际化 Key 使用 `mdm.*`。
- `mom-mdm-api` 提供已发布质量标准的引用契约；未来 QMS 通过 Client 使用引用，不跨 Schema 查询。
- QMS 拥有未来的检验单、采样、结果、判定、质量冻结、放行和不合格处置等运行时事实。
- QMS 不再提供质量定义 CRUD；旧 `mom_qms` 定义表通过新的 Flyway Migration 退出，不修改历史 Migration。

## 边界与后果

- MDM 发布的质量标准是可引用主数据，不等于一次检验任务或结果。
- QMS 使用定义时必须保留足以解释历史结果的版本引用或快照策略；该策略在 QMS 运行时 Slice 中决定，本 ADR 不提前实现。
- MDM 内部以本地事务维护定义及其修订项；本次迁移不引入跨服务事务、消息复制或缓存。
- 工厂、物料和 UOM 引用由 MDM 在自身边界内验证；未来外部调用只能使用公开 API/Client 契约。

## 未决定

- QMS 检验状态机、抽样方案、判定和放行语义；
- 已发布标准在 QMS 中采用版本引用还是不可变快照；
- SAP/LIMS 同步方向、幂等与失败补偿。

这些内容属于后续业务 Slice，不得从本 ADR 推导为已实现能力。

## 验证

- MDM Migration、PostgreSQL 集成测试与 Controller 契约测试覆盖定义持久化和引用校验。
- QMS Migration 测试证明旧定义表退出，源码不再暴露定义 CRUD。
- Web 路由和 API 测试证明质量定义使用 MDM 前缀与权限。
