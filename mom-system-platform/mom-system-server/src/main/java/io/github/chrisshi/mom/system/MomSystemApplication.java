package io.github.chrisshi.mom.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MOM System Platform 的运行时入口。
 *
 * <p>S13 在 S12 宿主上启用独立 mom_system PostgreSQL、类型化非敏感参数与统一 JWT Resource Server。
 * Redis 仅由既有安全组件检查 revoked sid，不参与参数存储或缓存；未启用消息、Seata、Feign Client 或
 * 定时任务。数据库、JWT 或安全撤销基础设施不可用时对应请求 Fail Closed，不返回伪造默认参数。</p>
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
