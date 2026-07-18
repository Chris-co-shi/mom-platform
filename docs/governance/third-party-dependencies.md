# 第三方依赖来源与许可证记录

本文件记录 MOM Platform 主动新增且需要持续审查的第三方依赖。版本权威来源仍为根目录 `pom.xml`。

| 依赖 | 当前版本 | 使用范围 | 官方来源 | 许可证 | 说明 |
|---|---:|---|---|---|---|
| Project Lombok | 1.18.46 | Java 编译期注解处理；用于生成 getter、setter 等机械代码 | https://projectlombok.org/ / https://github.com/projectlombok/lombok | MIT License | 仅作为 `provided` 编译依赖和 Maven annotation processor，不进入应用运行时依赖；禁止在持久化实体上使用 `@Data` 自动生成身份相关方法。 |

## 维护规则

- 升级版本前必须确认目标 JDK 与 IDE/编译器兼容性。
- 新增依赖必须记录官方来源、许可证、运行时影响和替代方案。
- 编译期依赖不得因为使用方便而被打入业务应用运行时包。
