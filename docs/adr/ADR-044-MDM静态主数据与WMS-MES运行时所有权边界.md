# ADR-044：MDM 静态主数据与 WMS/MES 运行时所有权边界

- 状态：Accepted
- 提出日期：2026-10-14
- 接受日期：2026-10-14
- 决策人：Chris
- 关联：[ADR-025](ADR-025-IAM-System-MDM-WMS-EAM数据所有权边界.md)、[ADR-026](ADR-026-MOM业务表禁止物理外键与关联完整性策略.md)、[ADR-042](ADR-042-MOM渐进式分层与对象模型.md)

> 本 ADR 在 Platform Administration MVP 三大基础模块收口范围内接受。它只替代 ADR-025 中 Warehouse、Storage Location/Bin 及 Production Line/Work Center 的静态主数据所有权部分，不改变 ADR-025 的 IAM、System、Party、EAM 和用户偏好决策。

## 1. Context

当前 MDM 实际实现了工厂、车间、产线、工位、仓库、库区、库位、库位类型、物料、物料分类、计量维度、单位类别、计量单位和换算规则。ADR-025 原决策将 Warehouse 与 Storage Location/Bin 的整体权威交给 WMS，并将 Production Line/Work Center 的生产能力与排程语义留给 MES，未区分静态主档与业务运行状态。

如果按现有实现把所有仓储和生产状态都归入 MDM，会让 MDM 吸收 WMS/MES 的业务生命周期；如果把现有对象整体迁出，则会破坏已完成的 MDM 管理链条并造成重复主档。本决策将主档身份与运行时业务事实拆开。

## 2. Decision

### 2.1 MDM 拥有的静态主数据

MDM 是以下对象稳定身份和静态描述的唯一写入权威：

- 制造组织结构：Plant、Workshop、Production Line、Workstation。
- 仓储结构：Warehouse、Warehouse Area、Location Type、Location。
- 物料与计量：Material、Material Category、Dimension、UOM Category、UOM、UOM Conversion Rule。

对上述对象，MDM 维护稳定 ID、编码、名称、描述、静态父子关系及基础启停状态。编码唯一性、父子关系、逻辑删除/停用规则和乐观版本遵循 MDM 当前 API、表结构与业务约束。

### 2.2 WMS 拥有的仓储运行时事实

WMS 后续拥有库存数量与流水、占用、容器/托盘所在库位、收发存作业、库位可用性和作业生命周期。容量或混放规则若影响实时收储决策，也由 WMS 定义和执行；MDM 不把这些状态复制到静态主档。

Warehouse 与 Location 的静态启停只表示主数据是否可被新业务引用，不代表库位当前空闲、可入库或有可用容量。WMS 对其运行时约束保持独立权威。

### 2.3 MES 拥有的制造运行时事实

MES 后续拥有 Production Line/Workstation 的生产能力、排程、生产日历、工单执行及运行时可用状态。MDM 维护这些结构对象的稳定身份、编码、名称、静态层级和基础启停状态，不决定工单是否可派工或设备是否可生产。

设备资产及设备台账仍由 EAM 拥有；Workstation 是生产结构中的作业位置，不取代 Machine/Equipment Asset。

### 2.4 跨服务引用

- WMS 与 MES 以 MDM 字符串 ID 引用静态主数据，不建立跨 Schema 外键、级联或 JOIN。
- WMS/MES 保存单据、流水或追溯事实时，可按其业务规则固化当时需要的不可变展示快照；快照不是新的主数据权威。
- 本轮不增加跨服务同步、缓存、消息或事件机制。后续确有读取性能或状态传播需求时，在具体业务 Slice 中定义 API/事件、失效语义和失败处理。
- MDM 主数据停用或层级变化不回写篡改历史业务单据；新业务是否允许引用由 MDM 静态状态及 WMS/MES 自身运行规则共同决定。

## 3. Scope and Non-goals

本决策用于关闭当前 MDM V1 并约束未来 WMS/MES 建模。它不实现 WMS 库存、MES 工单、设备资产管理、排程、库位容量策略或跨域集成；不迁移现有 MDM 表和数据；不新增服务、依赖、Schema 或消息基础设施。

## 4. Alternatives

1. **Warehouse 全部归 WMS，MDM 不管理 Warehouse/Location**：与已实现管理链冲突，需要迁移或重复主档，暂不采用。
2. **MDM 管理静态 Warehouse/Location，WMS 管理运行时库存和作业**：采用。保留稳定主档，同时把运行时语义交给专业业务域。
3. **MDM 同时管理静态和运行时状态**：拒绝。会让 MDM 持有库存、占用和作业生命周期，并形成 WMS 第二权威。
4. **WMS/MES 各自复制一套主档**：拒绝。会导致编码、层级和启停状态分叉。

## 5. Consequences and Risks

- 当前 MDM 不需要数据迁移；现有表继续由 MDM Schema 单独拥有。
- WMS/MES 设计可以直接引用 MDM ID，但其具体业务 Slice 仍需明确跨服务读取、静态停用后的行为和历史快照字段。
- 管理人员可能把“MDM 启用”误解为“当前可执行作业”。接口和页面必须将其表达为主数据启停，不展示库存可用、产能可用等运行时含义。
- 若未来需要容量、生产能力等配置，必须先判断它是稳定主档属性还是随业务过程变化的运行时事实，再由对应业务方案和 ADR 决定归属。

## 6. Verification

接受本决策时核对当前 MDM API、Schema 与页面范围；实施收口时验证 MDM 只在自身 Schema 写入，且不含库存、容器占位、工单运行或设备资产状态字段。后续 WMS/MES Slice 必须提供以 MDM 字符串 ID 引用、无跨 Schema 外键/JOIN 的验证证据。
