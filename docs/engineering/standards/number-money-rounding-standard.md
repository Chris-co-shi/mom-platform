# 数字、金额与舍入工程规范

## 1. Wire Contract

API 不返回 `1,234.56`、`1.234,56` 等本地化数值字符串。普通计数可使用 JSON Number；技术 ID 和超过 JavaScript Safe Integer 的整数使用 String。

MOM 对金额、数量、浓度、比例等精度敏感值统一采用规范化 Decimal String 作为跨前端契约，例如 `"1234.567890"`。只允许十进制符号和可选负号，不含分组、指数、前导加号或 Locale 符号；字段必须定义允许的 scale、范围和尾零语义。Java 使用 `BigDecimal`，PostgreSQL 使用由领域范围确定的 `numeric(precision, scale)`；禁止 float/double。

该选择不改变现有公开 API；发现既有 JSON Number 契约时先登记兼容影响，不在治理 Slice 静默改型。

## 2. 精度与舍入

存储精度、计算精度、结算精度、展示精度和设备有效位数分别定义。除法必须指定 Scale 和 `RoundingMode`；中间步骤避免过早舍入；展示舍入不得写回权威值。财务、库存、配方和质量规则可以不同，平台不默认 `HALF_UP`、`HALF_EVEN` 或 `DOWN`。

## 3. 金额与货币

金额由 Decimal String `amount` 与 ISO 4217 `currency` 共同表达。Locale、时区和用户偏好不能推导货币，平台不设置全局默认货币。汇率需要来源、时间和版本，且不属于普通计量单位换算；S05 不实现汇率。前端只在展示层按 Locale 决定符号与分隔符。

## 4. 比例和百分比

默认比例契约使用 `ratio`，值域由领域定义，常规比例基数为 `0..1` Decimal String；`0.125` 展示为 `12.5%`。若业务直接传百分数，字段必须显式命名 `percentage` 并定义基数，不能与 ratio 混用。浓度、湿度、良率和损耗率必须分别声明 Quantity Kind、单位、范围和 scale。

官方事实以 [PostgreSQL 17 Numeric](https://www.postgresql.org/docs/17/datatype-numeric.html)、[ECMA-402 Intl](https://tc39.es/ecma402/) 和 [ISO 4217](https://www.iso.org/standard/64758.html) 为准；Decimal String 是 MOM 项目决策。
