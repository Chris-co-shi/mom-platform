package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.mdm.application.MdmDataProbeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * MDM PostgreSQL 数据访问技术探针。
 *
 * <p>该接口用于 P01-S04 验证可执行 Jar 中的 Flyway、MyBatis-Plus、事务和 PostgreSQL 连接，不是
 * 主数据业务 API。只有显式设置 {@code mom.mdm.data-probe.enabled=true} 时才注册，默认关闭，避免
 * 技术验证接口在普通启动或生产环境中意外暴露。</p>
 *
 * <p>启用该接口时数据库基础设施必须已经就绪；否则应用启动失败属于预期的 fail-closed 行为。
 * 路径位于 {@code /internal}，后续正式安全基线还应增加内部网络和权限限制。</p>
 */
@RestController
@RequestMapping("/internal/mdm/data-probes")
@ConditionalOnProperty(
        prefix = "mom.mdm.data-probe",
        name = "enabled",
        havingValue = "true")
public class MdmDataProbeController {

    private final MdmDataProbeService service;

    /**
     * 创建数据技术探针 Controller。
     *
     * @param service 显式事务服务
     */
    public MdmDataProbeController(MdmDataProbeService service) {
        this.service = service;
    }

    /**
     * 向 PostgreSQL 写入一条技术验证记录。
     *
     * @param request 验证键和值
     * @return HTTP 201 以及包含主键、UTC 审计字段和版本号的响应
     */
    @PostMapping
    public ResponseEntity<MdmDataProbeResponse> create(
            @RequestBody MdmDataProbeRequest request) {
        MdmDataProbeResponse response = MdmDataProbeResponse.from(
                service.create(request.probeKey(), request.probeValue()));
        return ResponseEntity.created(URI.create(
                        "/internal/mdm/data-probes/" + response.probeKey()))
                .body(response);
    }

    /**
     * 根据验证键读取 PostgreSQL 记录。
     *
     * @param probeKey 技术验证键
     * @return 存在时返回 HTTP 200，不存在时返回 HTTP 404
     */
    @GetMapping("/{probeKey}")
    public ResponseEntity<MdmDataProbeResponse> findByKey(
            @PathVariable String probeKey) {
        return service.findByKey(probeKey)
                .map(MdmDataProbeResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
