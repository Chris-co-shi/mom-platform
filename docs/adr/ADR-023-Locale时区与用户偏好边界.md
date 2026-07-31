# ADR-023：Locale、时区与用户偏好边界

- 状态：Accepted
- 日期：2026-07-29
- 决策范围：P1.6 S05

## 背景

MOM 同时面向浏览器、移动设备、跨时区工厂和精度敏感的工业数据。当前 Web 已存在 `zh-CN`、`en-US` 静态资源和本地偏好，平台尚无 Factory 时区、单位目录或用户偏好权威模型。若把显示偏好混入 IAM、JWT 或业务事实，将同时破坏授权边界、历史可追溯性和跨端一致性。

## 决策

1. 初始 Locale 只支持 BCP 47 Tag `zh-CN`、`en-US`，默认回退 `zh-CN`；支持列表和 Alias 表必须显式维护。
2. IAM 只拥有账号身份和认证协议。未来 System 拥有用户 Locale、显示时区、显示格式和应用视图偏好；MDM 拥有 Factory 业务时区、单位目录和换算关系；业务领域拥有金额、数量和单位事实。
3. 技术时间点统一为 `Instant`、PostgreSQL `timestamptz` 和 RFC 3339 字符串；用户显示时区与 Factory 业务时区严格分离，长期偏好使用 IANA Zone ID。
4. 精度敏感的金额、数量、浓度和比例在跨前端 JSON 契约中使用规范化 Decimal String；Java 使用 `BigDecimal`，PostgreSQL 使用有界 `numeric(precision, scale)`。
5. 金额由 `amount + currency` 表达，货币使用 ISO 4217；计量单位采用稳定 Code，优先评估 UCUM，无法表达时使用受控 MOM 扩展并维护映射。
6. Web/Mobile 各自拥有静态翻译；后端服务拥有固定校验和错误说明；只有 S15 出现真实动态调用方时，System 才建立动态国际化能力。S16 才实现类型化、白名单、版本化的用户偏好。
7. Locale、时区和显示单位都不是权限、数据范围或 Token Claim。客户端 Header 只能在未来经 ADR 批准后影响展示，不能决定业务日期、授权或领域状态。

## 所有权矩阵

| 能力 | 权威所有者 |
|---|---|
| 认证协议和账号身份 | IAM |
| Locale、显示时区、显示格式、应用视图偏好 | System |
| Factory 与 Factory 业务时区 | MDM |
| 单位目录和换算关系 | MDM |
| 金额、数量和单位实际值 | 对应业务领域 |
| Web/Mobile 静态翻译 | 各客户端应用 |
| 后端固定消息 | 各后端服务 |
| 动态运行时国际化资源 | System；仅 S15 有真实调用方时 |

身份关系为 `MDM Person/Employee -> IAM Account -> System User Preference`，本 ADR 不创建任何表、API 或 Token Claim。

## 理由

- BCP 47、IANA TZDB、RFC 3339、ISO 4217 和 UCUM 提供跨语言、跨时区和量值交换的稳定标识。
- 固定 UTC Offset 无法表达 DST；Locale 也不能推导货币、时区或业务日期。
- JavaScript `Number` 无法无损覆盖全部十进制工业量值，Decimal String 避免解析阶段提前转为二进制浮点数。
- 偏好不是授权，将其从 IAM 和 JWT 中分离可以独立演进并在每次使用时重新校验 Factory Scope。

## 后果与风险

- 客户端需要显式 Decimal 库或字符串到 Decimal 的受控转换，不能直接算术处理字符串。
- Factory 时区、单位换算和用户偏好仍是后续实现缺口；没有权威数据时不得伪造实现。
- DST 模糊或不存在时间必须由具体业务裁决并测试，不能依赖 JDK 默认猜测。
- S15、S16 的实现必须分别证明动态国际化调用方和偏好消费者存在。

## 官方依据

- [BCP 47 / RFC 5646](https://www.rfc-editor.org/info/rfc5646/)
- [HTTP Accept-Language](https://www.rfc-editor.org/rfc/rfc9110.html#name-accept-language)
- [OpenID Connect Core ui_locales](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest)
- [Java 25 Locale](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Locale.html)
- [Java 25 ZoneId](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/ZoneId.html)
- [IANA Time Zone Database](https://www.iana.org/time-zones)
- [RFC 3339](https://www.rfc-editor.org/info/rfc3339/)
- [Unicode CLDR](https://cldr.unicode.org/)、[ECMA-402 Intl](https://tc39.es/ecma402/)
- [ISO 4217](https://www.iso.org/standard/64758.html)、[UCUM](https://ucum.org/)

## 验证

- S05 专题规范和现状清单必须被工程基线索引。
- 轻量门禁只扫描生产源码和资源，并用正反例证明不误扫注释、字符串、测试或文档。
- 本 Slice 不修改 `mom-web`、`mom-mobile`、公开 API、IAM、Schema 或 Flyway。

## S16 实施记录

S16 在不改变本 ADR `Accepted` 状态和既有所有权裁决的前提下，已于 System Platform 落地两张无物理外键的用户体验表：类型化显示偏好与受限视图设置。显示偏好只允许 `zh-CN`/`en-US`、Java `ZoneId` 可验证的 IANA 显示时区、`SYSTEM`/`LIGHT`/`DARK`、`COMFORTABLE`/`COMPACT` 和 10/20/50/100 Page Size；平台默认固定为 `zh-CN`、`UTC`、`SYSTEM`、`COMFORTABLE`、`20`。

用户身份只来自 JWT `sub`，Preference 不进入 JWT、不修改 `/api/iam/me`、不参与授权，也不改变 Factory 业务时区、业务日期、金额、数量或单位事实。Default/Last Factory、Default Application、Dashboard/Favorites 与客户端接入均保持 Deferred；本次未引入缓存、消息或跨服务同步。
