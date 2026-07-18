package io.github.chrisshi.mom.mdm.client;

import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "mom-mdm-server", contextId = "mdmServiceProbeClient")
public interface MdmServiceProbeClient {

    @GetMapping("/internal/mdm/probe")
    MdmServiceProbeResponse probe();
}
