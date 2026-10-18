package io.github.chrisshi.mom.webmvc.autoconfigure;

import io.github.chrisshi.mom.coderule.application.CodeRuleApplication;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleController;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleManagementAccess;
import org.springframework.beans.factory.annotation.Value;
import io.github.chrisshi.mom.webmvc.coderule.CodeRuleExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** 只在 Owner 显式启用规则且开启 Resource Server 后暴露公共 HTTP 入口。 */
@AutoConfiguration(afterName = "io.github.chrisshi.mom.coderule.autoconfigure.CodeRuleAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "mom.code-rule", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "mom.security.resource-server", name = "enabled", havingValue = "true")
public class CodeRuleWebMvcAutoConfiguration {
    /** 权限只取自当前服务启动配置；缺失或不属于 Owner 的权限配置拒绝访问。 */
    @Bean("codeRuleManagementAccess")
    @ConditionalOnBean(CodeRuleApplication.class)
    CodeRuleManagementAccess codeRuleManagementAccess(
            @Value("${mom.code-rule.owner:}") String owner,
            @Value("${mom.code-rule.management.read-authority:}") String read,
            @Value("${mom.code-rule.management.write-authority:}") String write) {
        return new CodeRuleManagementAccess(owner, read, write);
    }

    @Bean @ConditionalOnBean({CodeRuleApplication.class, CodeSegmentRegistry.class})
    CodeRuleController codeRuleController(CodeRuleApplication application, CodeSegmentRegistry handlers) {
        return new CodeRuleController(application, handlers);
    }
    @Bean @ConditionalOnBean(CodeRuleController.class)
    CodeRuleExceptionHandler codeRuleExceptionHandler(I18nMessageResolver messages) {
        return new CodeRuleExceptionHandler(messages);
    }
}
