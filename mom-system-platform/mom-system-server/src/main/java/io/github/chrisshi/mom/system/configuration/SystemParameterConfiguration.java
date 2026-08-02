package io.github.chrisshi.mom.system.configuration;

import io.github.chrisshi.mom.system.domain.parameter.ParameterValueNormalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * System Parameter 纯领域服务的装配配置。
 *
 * <p>该类型只连接统一 Jackson ObjectMapper 与规范化器，不包含业务判断、数据库连接或缓存。ObjectMapper
 * 不可用时应用启动失败，禁止回退到第二套 JSON Parser。</p>
 */
@Configuration(proxyBeanMethods = false)
public class SystemParameterConfiguration {

    /** 创建线程安全复用的参数值规范化器。 */
    @Bean
    ParameterValueNormalizer parameterValueNormalizer(ObjectMapper objectMapper) {
        return new ParameterValueNormalizer(objectMapper);
    }
}
