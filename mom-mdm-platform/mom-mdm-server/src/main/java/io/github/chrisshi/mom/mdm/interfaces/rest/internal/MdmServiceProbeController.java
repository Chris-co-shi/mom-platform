package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MDM 内部服务发现技术探针。
 *
 * <p>该 Controller 仅用于 Phase 01 验证 Nacos 注册、OpenFeign 编解码与关联标识传播。路径位于
 * {@code /internal}，后续接入 Gateway 鉴权后不得作为外部业务接口暴露，也不得在此承载主数据 CRUD。</p>
 */
@RestController
@RequestMapping("/internal/mdm")
public class MdmServiceProbeController {

    /**
     * 返回 MDM 技术状态及当前请求关联标识。
     *
     * @return MDM 技术探针响应
     */
    @GetMapping("/probe")
    MdmServiceProbeResponse probe() {
        return new MdmServiceProbeResponse(
                "mom-mdm-server",
                "UP",
                CorrelationContext.resolveOrGenerate(CorrelationContext.currentId()));
    }
}
