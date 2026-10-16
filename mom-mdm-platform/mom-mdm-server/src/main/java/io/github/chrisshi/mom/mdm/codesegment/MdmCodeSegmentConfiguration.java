package io.github.chrisshi.mom.mdm.codesegment;

import io.github.chrisshi.mom.core.codesegment.CodeSegmentHandler;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 汇总当前 MDM Owner 中由 Spring 收集的编码片段 Handler。
 */
@Configuration(proxyBeanMethods = false)
public class MdmCodeSegmentConfiguration {
    @Bean
    CodeSegmentRegistry mdmCodeSegmentRegistry(List<CodeSegmentHandler> handlers) {
        return new CodeSegmentRegistry(handlers);
    }
}
