package io.github.chrisshi.mom.webmvc.autoconfigure;

import io.github.chrisshi.mom.core.coderule.CodeRuleApplication;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentRegistry;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleController;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** 只在 Owner 显式启用规则且开启 Resource Server 后暴露公共 HTTP 入口。 */
@AutoConfiguration(afterName = "io.github.chrisshi.mom.data.coderule.CodeRuleDataAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "mom.code-rule", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "mom.security.resource-server", name = "enabled", havingValue = "true")
public class CodeRuleWebMvcAutoConfiguration {
    @Bean @ConditionalOnBean({CodeRuleApplication.class, CodeSegmentRegistry.class})
    CodeRuleController codeRuleController(CodeRuleApplication application, CodeSegmentRegistry handlers) {
        return new CodeRuleController(application, handlers);
    }
    @Bean @ConditionalOnBean(CodeRuleController.class)
    CodeRuleExceptionHandler codeRuleExceptionHandler(I18nMessageResolver messages) {
        return new CodeRuleExceptionHandler(messages);
    }
}
