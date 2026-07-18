package io.github.chrisshi.mom.integration.client;

import io.github.chrisshi.mom.integration.api.seata.IntegrationSeataAtParticipantRequest;
import io.github.chrisshi.mom.integration.api.seata.IntegrationSeataAtParticipantResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Integration Seata AT 技术参与者 OpenFeign Client。
 *
 * <p>Client 只依赖 {@code mom-integration-api}，不依赖 Integration Server。Spring Cloud Alibaba Seata
 * 在同步调用链上负责传播 XID；该接口本身不手工拼接 Seata Header，避免业务代码依赖协议细节。未配置固定
 * URL 时以 {@code mom-integration-server} 作为服务发现标识，CI 可以按 {@code contextId} 覆盖 URL。</p>
 */
@FeignClient(
        name = "mom-integration-server",
        contextId = "integrationSeataAtParticipantClient")
public interface IntegrationSeataAtParticipantClient {

    /**
     * 调用 Integration 的受控 AT 技术参与者。
     *
     * @param request 技术参与者写入和故障注入参数
     * @return 参与者观察到的事务键、XID 和执行状态
     * @throws RuntimeException 远端拒绝、超时或参与者事务失败时由 Feign 抛出
     */
    @PostMapping("/internal/integration/seata-at/participants")
    IntegrationSeataAtParticipantResponse participate(
            @RequestBody IntegrationSeataAtParticipantRequest request);
}
