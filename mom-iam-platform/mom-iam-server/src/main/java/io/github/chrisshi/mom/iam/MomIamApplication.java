package io.github.chrisshi.mom.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MOM IAM 服务启动入口。
 *
 * <p>S03 在 S02 PostgreSQL、Flyway、MyBatis-Plus 与领域模型之上启用 Authorization Server、OIDC、
 * Authorization Code + PKCE、账号密码认证和四个 Public Client。S04 完整权限 Claims 与 S05 自定义
 * Refresh Rotation、Session 撤销仍不在本 Slice 实现。</p>
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
