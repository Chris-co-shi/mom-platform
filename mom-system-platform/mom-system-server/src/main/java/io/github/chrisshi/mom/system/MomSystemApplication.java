package io.github.chrisshi.mom.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MOM System Platform 的运行时入口。
 *
 * <p>System V1 只承载 Dictionary、SupportedLocale 与 {@code system.*} 动态 I18n。业务调用遵循
 * Controller → Application → Infrastructure，PostgreSQL 是唯一事实源；Framework 提供 Runtime HTTP、
 * classpath 技术消息与单实例 SSE 失效通知。服务不拥有 Parameter、User Preference、Application Catalog、
 * Navigation、发布快照、Cache、MQ 或 Outbox。数据库或认证基础设施不可用时请求 Fail Closed。</p>
 */
@SpringBootApplication
public class MomSystemApplication {

    /**
     * 启动 System Platform。
     *
     * @param args 命令行配置参数；方法只委托 Spring Boot 启动，不修改参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MomSystemApplication.class, args);
    }
}
