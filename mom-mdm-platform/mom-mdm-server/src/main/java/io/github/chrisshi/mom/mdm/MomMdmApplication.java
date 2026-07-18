package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.integration.client.IntegrationSeataAtParticipantClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * MOM MDM 服务启动入口。
 *
 * <p>MDM 仍是独立主数据服务。Phase 01 仅显式启用一个指向 Integration 技术参与者的 Feign Client，用于
 * 受控 Seata AT PoC；该依赖不允许扩展为 MDM 直接调用 Integration Server 内部实现。Seata 默认关闭，
 * 普通启动不会连接 TC 或代理数据源。</p>
 */
@SpringBootApplication
@EnableFeignClients(clients = IntegrationSeataAtParticipantClient.class)
public class MomMdmApplication {

    /**
     * 启动 MDM Spring Boot 应用。
     *
     * @param args 命令行配置参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MomMdmApplication.class, args);
    }
}
