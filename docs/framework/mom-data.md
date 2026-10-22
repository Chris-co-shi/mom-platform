# mom-data：MyBatis-Plus 与分页适配

**状态：Auth、MDM、WMS 等业务采用。** 解决数据库访问的共同配置、分页转换与 Entity 审计填充。源码入口为 [`mom-data`](../../mom-framework/mom-data/)；SQL 和数据 Owner 仍属于各业务模块。

- 推荐入口：Application 用 `PageAdapter.toPage(PageQuery<?>)` 在 SQL 前校验 `mom.data.pagination.max-page-size`，Mapper 查询后用 `PageAdapter.toResult(IPage<T>)` 转为 `PageResult<T>`；需要记录类型转换时使用其映射重载。Auth、MDM、WMS 的分页 Application 有真实调用。
- Mapper/Entity：`MomBaseMapper` 和 `Base*Entity` 为现有持久化基础类型；`MomDataAutoConfiguration` 装配 MyBatis-Plus 拦截器、分页与 `MomMetaObjectHandler`。写入前仍需按业务定义字段、Mapper 和事务，不因继承基类获得领域规则。
- 失败与限制：非法分页参数在执行 SQL 前失败；审计填充只覆盖受支持的 Entity 写入路径，Wrapper-only 更新和自定义 SQL 不会自动补业务 Actor。缺少 Actor 的现行行为与目标约束存在差距，见[审计规范](../engineering/standards/audit-concurrency-lifecycle-standard.md)。

**必须遵守：** 复用已有分页转换，不手工拆 `records/total/current/size`；数据库结构走 Flyway，禁止跨 Schema 业务 JOIN/外键。详细 SQL、Mapper 和 Entity 规则见[持久化规范](../engineering/standards/persistence-data-modeling-standard.md)。
