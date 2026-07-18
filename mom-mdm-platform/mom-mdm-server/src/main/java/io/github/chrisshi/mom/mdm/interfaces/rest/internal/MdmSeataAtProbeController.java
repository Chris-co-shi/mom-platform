package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.mdm.application.MdmSeataAtProbeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * MDM Seata AT 受控全局事务技术接口。
 *
 * <p>接口默认关闭，仅在 {@code mom.mdm.seata-at-probe.enabled=true} 时注册。它用于 Phase 01 验证两套
 * PostgreSQL 数据库之间的短 AT 事务，不是主数据业务 API，也不能被复制到长制造流程中。</p>
 */
@RestController
@RequestMapping("/internal/mdm/seata-at-probes")
@ConditionalOnProperty(
        prefix = "mom.mdm.seata-at-probe",
        name = "enabled",
        havingValue = "true")
public class MdmSeataAtProbeController {

    private final MdmSeataAtProbeService service;

    /**
     * 创建 MDM Seata AT 技术 Controller。
     *
     * @param service 受控全局事务服务
     */
    public MdmSeataAtProbeController(MdmSeataAtProbeService service) {
        this.service = service;
    }

    /**
     * 发起一个短全局事务。
     *
     * @param request 两端写入值与故障注入参数
     * @return HTTP 201 以及两端一致的 XID；失败场景返回 5xx 并由调用方核对数据库最终状态
     */
    @PostMapping
    public ResponseEntity<MdmSeataAtProbeResponse> execute(
            @RequestBody MdmSeataAtProbeRequest request) {
        MdmSeataAtProbeResponse response = service.execute(request);
        return ResponseEntity.created(URI.create(
                        "/internal/mdm/seata-at-probes/" + response.transactionKey()))
                .body(response);
    }
}
