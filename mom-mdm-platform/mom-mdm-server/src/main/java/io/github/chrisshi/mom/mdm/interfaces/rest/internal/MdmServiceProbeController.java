package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/mdm")
public class MdmServiceProbeController {

    @GetMapping("/probe")
    MdmServiceProbeResponse probe() {
        return new MdmServiceProbeResponse(
                "mom-mdm-server",
                "UP",
                CorrelationContext.resolveOrGenerate(CorrelationContext.currentId()));
    }
}
