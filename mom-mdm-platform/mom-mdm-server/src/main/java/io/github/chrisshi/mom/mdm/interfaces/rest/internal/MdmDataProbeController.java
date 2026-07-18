package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.mdm.application.MdmDataProbeService;
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
 * 主数据业务 API。路径位于 {@code /internal}，后续安全基线完成后必须限制为内部运维或测试访问。</p>
 */
@RestController
@RequestMapping("/internal/mdm/data-probes")
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
