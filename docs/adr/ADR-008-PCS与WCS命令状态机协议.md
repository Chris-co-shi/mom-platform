# ADR-008：PCS 与 WCS 命令状态机协议

- 状态：Accepted
- 日期：2026-07-18
- 关联文档：[集成架构](../architecture/集成架构.md)

## 1. 背景

PCS/WCS 执行具有长时间、异步、可重试、可能断线和需要人工接管的特点。若 MOM 直接依赖 PLC 地址、OPC UA NodeId 或具体设备 SDK，会造成领域模型和协议实现强耦合。

## 2. 候选方案

- MOM 直接调用设备协议：实现快，但协议类型泄漏、难模拟和难恢复。
- 同步 RPC 等待设备完成：调用链简单，但无法承受长任务和断线。
- 统一业务命令 + 异步状态机 + 协议适配器：边界清晰，可恢复和可测试。

## 3. 决策

MOM 只使用统一业务协议：

- `DeviceCommand`
- `CommandAccepted`
- `CommandProgress`
- `CommandCompleted`
- `CommandFailed`

每个命令必须包含唯一 `command_id`、设备/资源标识、业务关联标识、期望动作、超时和版本。

PCS/WCS 内部通过 ProtocolAdapter 隔离 MQTT、OPC UA、PLC4X 和模拟协议。

## 4. 状态机

建议基础状态：

```text
CREATED → SENT → ACCEPTED → EXECUTING → COMPLETED
                         ↘ FAILED
                         ↘ TIMED_OUT → RECOVERING → EXECUTING/FAILED
                         ↘ MANUAL_TAKEOVER → COMPLETED/CANCELLED
```

重复、乱序或过期回执必须根据 `command_id`、状态版本和状态迁移规则处理。

## 5. 后果

正向：MOM 与设备协议解耦，支持模拟器、重试、故障恢复和面试演示。

负向：需要维护命令存储、状态机、超时扫描和人工处理界面。

## 6. 验证方式

- 断线后恢复并继续执行。
- 重复回执不重复完成业务。
- 乱序回执不能让状态倒退。
- 超时后支持自动重试或人工接管。
- Trace 能关联 MOM、MQ、PCS/WCS 和设备模拟器。

## 7. 替代条件

具体协议库可以替换，但统一命令、状态机和幂等边界不能被设备 SDK 直接替代。