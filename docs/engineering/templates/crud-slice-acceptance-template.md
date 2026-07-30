# CRUD Slice 验收：<capability>

## 边界与实现路径

- Web/Application/Domain/Infrastructure 职责：
- Entity 基类与表生命周期：
- 单表操作清单及 MyBatis-Plus 覆盖：
- 自定义 SQL 清单、必要性和 PostgreSQL 证据：
- 多表关系、写入顺序、分页方式和索引映射：
- 删除/停用/归档与无物理 FK 完整性：

## 行为证据

| 场景 | 测试/命令 | 结果 |
|---|---|---|
| 成功 Create/Read/Update | | |
| Bean Validation / 错误模型 | | |
| 唯一冲突 / 幂等重放 | | |
| 引用不存在 / 删除保护 | | |
| 乐观锁 / affected rows | | |
| 批量上限 / 部分失败 | | |
| 多表事务回滚 / Outbox | | |
| 分页、排序、N+1 调用次数 | | |
| 审计与敏感信息 | | |
| PostgreSQL Migration/孤儿/索引 | | |

## 门禁与结论

- CRUD / Schema / No-FK / Persistence / Architecture 门禁：
- 未完成项、精确例外、风险、责任 Slice 和退出条件：
- 最终实现是否实际采用规范技术路径：是 / 否（规范文件存在本身不构成验收）
