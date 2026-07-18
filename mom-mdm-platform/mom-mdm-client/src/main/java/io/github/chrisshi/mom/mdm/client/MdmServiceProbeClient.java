package io.github.chrisshi.mom.mdm.client;

import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * MDM 技术探针的 OpenFeign Client。
 *
 * <p>Client 只依赖 {@code mom-mdm-api} 契约，不依赖 MDM Server。未配置固定 URL 时，Feign 以
 * {@code mom-mdm-server} 作为 LoadBalancer serviceId，通过 Nacos 选择实例；测试可以按照 contextId
 * 覆盖 URL，验证 HTTP 编解码而不伪装真实服务发现。</p>
 */
@FeignClient(name = "mom-mdm-server", contextId = "mdmServiceProbeClient")
public interface MdmServiceProbeClient {

    /**
     * 调用 MDM 内部技术探针。
     *
     * @return MDM 服务名称、状态和接收到的关联标识
     */
    @GetMapping("/internal/mdm/probe")
    MdmServiceProbeResponse probe();
}
