package io.github.chrisshi.mom.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MOM IAM 服务启动入口。
 *
 * <p>S02 只启用 PostgreSQL、Flyway、MyBatis-Plus 与持久化领域模型，不配置登录页、Authorization
 * Server 端点、JWT 签发、RBAC 运行时计算或管理 API。无数据库 Bootstrap 测试通过明确排除数据源保持轻量启动。</p>
 */
@SpringBootApplication
public class MomIamApplication {

    /**
     * 启动 IAM Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MomIamApplication.class, args);
    }
}
