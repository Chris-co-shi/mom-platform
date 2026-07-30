package io.github.chrisshi.mom.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MOM System Platform 的运行时入口。
 *
 * <p>S13/S14 在 S12 宿主上启用独立 mom_system PostgreSQL、类型化非敏感参数、受限非权威字典与统一
 * JWT Resource Server。Redis 仅由既有安全组件检查 revoked sid，不参与参数或字典缓存；未启用消息、
 * Seata、Feign Client 或定时任务。数据库、JWT 或安全撤销基础设施不可用时请求 Fail Closed。</p>
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
