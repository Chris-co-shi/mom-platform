# Package Layout 验收记录模板

## 1. 范围

- Bounded Context：
- 类型与职责：
- 当前目录 / Package：
- 目标目录 / Package：
- 是否公开 API；若是，稳定契约是什么：
- 所属层：Web / Application / Domain / Infrastructure / Configuration：
- Infrastructure Adapter：Persistence / Client / Messaging / Cache / Storage：
- Persistence 职责：Entity / Mapper / Repository / Query / Converter / TypeHandler：
- 为什么不能放入已有标准职责包：
- 是否会形成 `persistence.<feature>`；若是必须停止并重新设计：

## 2. 移动影响

- 是否存在同名类型或 FQCN 冲突：
- 当前与目标可见性；是否扩大及原因：
- Spring Component/Bean 名称是否变化：
- Component Scan / `@Import` / `@MapperScan`：
- Mapper XML 文件、Namespace、`resultType/resultMap/typeHandler`：
- 反射、序列化、配置字符串或 Service Loader 引用：
- 测试 Package 与 package-private 访问：
- 文档、白名单和 ArchUnit 精确类名：

## 3. 行为保持证据

- Package Layout Baseline：
- Architecture Tests：
- `test-compile`：
- 模块 `test`：
- 模块 `verify`：
- PostgreSQL IT / Mapper XML 加载：
- Packaged smoke / Readiness：
- Bean 数量与核心 Bean 名称：
- Flyway Version、SQL、HTTP/JSON、权限、事务未变的证据：

## 4. 精确例外

- 文件或类型：
- 不适用标准结构的架构角色：
- 风险与禁止复制边界：
- 退出条件和责任 Slice：
