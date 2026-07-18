package io.github.chrisshi.mom.integration.interfaces.rest.internal;

import io.github.chrisshi.mom.integration.api.seata.IntegrationSeataAtParticipantRequest;
import io.github.chrisshi.mom.integration.api.seata.IntegrationSeataAtParticipantResponse;
import io.github.chrisshi.mom.integration.application.IntegrationSeataAtParticipantService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Integration Seata AT 技术参与者内部接口。
 *
 * <p>接口默认关闭，仅在显式设置 {@code mom.integration.seata-at-probe.enabled=true} 时注册。它只服务于
 * Phase 01 兼容性和故障演练，不是正式外部集成 API。生产部署即使临时启用，也必须继续由内部网络和后续
 * 服务间认证保护。</p>
 */
@RestController
@RequestMapping("/internal/integration/seata-at/participants")
@ConditionalOnProperty(
        prefix = "mom.integration.seata-at-probe",
        name = "enabled",
        havingValue = "true")
public class IntegrationSeataAtParticipantController {

    private final IntegrationSeataAtParticipantService service;

    /**
     * 创建技术参与者 Controller。
     *
     * @param service Integration AT 本地分支服务
     */
    public IntegrationSeataAtParticipantController(
            IntegrationSeataAtParticipantService service) {
        this.service = service;
    }

    /**
     * 加入上游已创建的 Seata 全局事务并提交一个短本地分支。
     *
     * @param request 技术事务键、参与者值和故障注入参数
     * @return HTTP 201 以及参与者观察到的 XID
     */
    @PostMapping
    public ResponseEntity<IntegrationSeataAtParticipantResponse> participate(
            @RequestBody IntegrationSeataAtParticipantRequest request) {
        IntegrationSeataAtParticipantResponse response = service.participate(request);
        return ResponseEntity.created(URI.create(
                        "/internal/integration/seata-at/participants/"
                                + response.transactionKey()))
                .body(response);
    }
}
