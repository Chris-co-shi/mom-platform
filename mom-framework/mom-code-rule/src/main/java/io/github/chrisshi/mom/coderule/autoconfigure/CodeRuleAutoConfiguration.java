package io.github.chrisshi.mom.coderule.autoconfigure;

import io.github.chrisshi.mom.coderule.application.DefaultCodeRuleApplication;
import io.github.chrisshi.mom.coderule.application.DefaultCodeGenerationApplication;
import io.github.chrisshi.mom.coderule.application.CodeGenerationApplication;
import io.github.chrisshi.mom.coderule.field.CodeFieldProvider;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentHandler;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.store.JdbcCodeRuleStore;
import io.github.chrisshi.mom.coderule.application.CodeRuleApplication;
import io.github.chrisshi.mom.coderule.store.CodeRuleStore;
import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.coderule.handler.LiteralCodeSegmentHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

/** 仅显式启用 Owner 时装配公共 Handler、Registry、Application 与本地 JDBC Store。 */
@AutoConfiguration(afterName = "io.github.chrisshi.mom.data.config.MomDataAutoConfiguration")
@ConditionalOnProperty(prefix = "mom.code-rule", name = "enabled", havingValue = "true")
@ComponentScan(basePackageClasses = LiteralCodeSegmentHandler.class)
public class CodeRuleAutoConfiguration {
    @Bean CodeFieldRegistry codeFieldRegistry(List<CodeFieldProvider> providers) {
        return new CodeFieldRegistry(providers);
    }
    @Bean CodeSegmentRegistry codeSegmentRegistry(List<CodeSegmentHandler> handlers) {
        return new CodeSegmentRegistry(handlers);
    }
    @Bean CodeRuleStore codeRuleStore(DataSource source, PlatformTransactionManager transactions,
                                      JsonMapper json, CurrentActorProvider actors, Clock clock,
                                      org.springframework.core.env.Environment environment) {
        String owner = environment.getProperty("mom.code-rule.owner", "");
        if (!(transactions instanceof DataSourceTransactionManager manager) || manager.getDataSource() != source)
            throw new IllegalStateException("编码规则 Store 与本地事务必须使用同一 DataSource");
        long maxPageSize = environment.getProperty("mom.data.pagination.max-page-size", Long.class, 200L);
        return new JdbcCodeRuleStore(source, json, maxPageSize, actors, clock, owner);
    }
    @Bean CodeRuleApplication codeRuleApplication(CodeRuleStore store, CodeSegmentRegistry handlers,
                                                   CodeFieldRegistry fields, IdGenerator ids) {
        return new DefaultCodeRuleApplication(store, handlers, fields, ids);
    }
    @Bean CodeGenerationApplication codeGenerationApplication(CodeRuleStore store, CodeSegmentRegistry handlers,
                                                                CodeFieldRegistry fields, Clock clock) {
        return new DefaultCodeGenerationApplication(store, handlers, fields, clock);
    }
}
