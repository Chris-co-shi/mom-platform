# Third-Party Notices

本仓库采用“标准依赖直接复用、开源机制学习后重构、工业领域能力自研”的原则。

正式引入第三方源码或二进制依赖时，必须记录项目、版本或提交号、许可证、复用方式、源码修改及 NOTICE 要求。

当前骨架仅声明 Maven 依赖，不复制 Pig、Yudao Cloud、JetLinks、OpenWMS 等项目源码。

## 本地 API 文档依赖

- 项目：springdoc-openapi `3.1.0`（`springdoc-openapi-starter-webmvc-ui`）
- 官方来源：https://github.com/springdoc/springdoc-openapi
- 许可证：Apache License 2.0
- 复用方式：Auth、System、MDM 运行时二进制依赖，仅在本地 Profile 开放文档端点；未复制或修改项目源码。
- 分发说明：如发布包包含该依赖，应随构建产物保留其上游许可证及适用的 NOTICE；完整依赖登记见 `docs/governance/third-party-dependencies.md`。
