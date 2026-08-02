package io.github.chrisshi.mom.system.configuration;

import io.github.chrisshi.mom.iam.client.IamPermissionReferenceClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * System S18 运行时跨服务集成装配入口。
 *
 * <p>只注册 IAM Permission Reference Feign Client。服务身份、OAuth2 Token、超时、RocketMQ 和 Redis 参数均由
 *环境配置提供；Application 与 Domain 不依赖具体传输技术。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = IamPermissionReferenceClient.class)
public class SystemRuntimeIntegrationConfiguration {
}
