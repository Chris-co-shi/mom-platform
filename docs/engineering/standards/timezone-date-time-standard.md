# 时区、日期与时间工程规范

## 1. 六种语义

必须区分 UTC 时间点、用户显示时区、Factory 业务时区、设备时区、业务日期和临时客户端 Offset。技术时间点使用 Java `Instant`、PostgreSQL `timestamptz`、API RFC 3339 字符串（`Z` 或明确 Offset）。业务日期使用 `LocalDate`/`date`；不能用无 Offset 的 `LocalDateTime` 表达全球时间点。

用户显示时区归 System 偏好，Factory 业务时区归 MDM Factory，均使用 IANA Region Zone ID，例如 `Asia/Shanghai`。`UTC+8`、`GMT+8` 和 `+08:00` 不能作为长期偏好，因为固定 Offset 不包含 DST 规则。

## 2. API 和业务日期

| 语义 | Java | API |
|---|---|---|
| 时间点 | `Instant` | RFC 3339 字符串 |
| 日期 | `LocalDate` | `YYYY-MM-DD` |
| 月份 | `YearMonth` | `YYYY-MM` |
| 本地时间 | `LocalTime` | `HH:mm:ss` |
| 时区 | `ZoneId` | IANA Zone ID |
| 持续时间 | `Duration` | ISO-8601 Duration 或接口明确的秒数 |

API 和数据库格式不随 Locale 改变；时间范围统一为 `[from,to)`。禁止用 `23:59:59.999` 表达日末。普通查询由客户端把显示范围转换为 UTC；不为每个接口增加 `timezone`、`utcOffset` 或 `locale`。

“某 Factory 某业务日”由客户端提交 `factoryId + businessDate`，服务端从权威 MDM Factory 读取时区并换算 UTC 半开区间。用户显示时区和客户端 Header 均不得替代权威 Factory 时区。

## 3. 设备、调度与审计

设备优先上传 UTC 时间点和设备标识；必要时保留原始设备时间及 Offset 作为诊断数据。Cron 和定时任务必须声明 ZoneId，不能依赖宿主机默认时区。审计时间始终是 UTC 时间点；JWT 时间保持标准 NumericDate。

生产业务逻辑不得依赖 `Locale.getDefault()`、`ZoneId.systemDefault()`、`TimeZone.getDefault()` 或无注入 `Clock` 的当前时间作为不可重复业务裁决。允许在明确 UI 适配或诊断代码中使用系统默认值，但必须隔离并说明。

## 4. DST

使用 `ZoneRules.getValidOffsets(LocalDateTime)` 或等价显式判断：返回零个 Offset 表示不存在时间，不能静默平移；业务必须选择拒绝或明确采用下一个有效时间。返回两个 Offset 表示重复时间，调用必须携带 Offset，或由用例明确选择 earlier/later。班次、排产、设备任务和欧洲 Factory 日界线必须有不存在时间、重复时间和规则更新测试。

## 5. 展示与降级

前端按当前 Locale 和用户显示 ZoneId 格式化，后端普通列表不逐条返回本地化日期字符串。偏好不可用时，展示可按场景回退应用默认、Factory 时区或 UTC，但降级不得改变业务日期、授权、金额或权威事实。

官方事实以 [Java 25 ZoneId](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/ZoneId.html)、[ZoneRules](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/zone/ZoneRules.html)、[IANA TZDB](https://www.iana.org/time-zones)、[RFC 3339](https://www.rfc-editor.org/info/rfc3339/) 和 [PostgreSQL 17 日期时间类型](https://www.postgresql.org/docs/17/datatype-datetime.html) 为准。
