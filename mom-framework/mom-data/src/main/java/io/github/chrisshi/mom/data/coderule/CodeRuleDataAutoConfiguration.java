package io.github.chrisshi.mom.data.coderule;

import io.github.chrisshi.mom.core.coderule.*;
import io.github.chrisshi.mom.core.codesegment.*;
import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.coderule.handler.LiteralCodeSegmentHandler;
import io.github.chrisshi.mom.data.page.PageAdapter;
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
public class CodeRuleDataAutoConfiguration {
    @Bean CodeFieldRegistry codeFieldRegistry(List<CodeFieldProvider> providers) {
        return new CodeFieldRegistry(providers);
    }
    @Bean CodeSegmentRegistry codeSegmentRegistry(List<CodeSegmentHandler> handlers) {
        return new CodeSegmentRegistry(handlers);
    }
    @Bean CodeRuleStore codeRuleStore(DataSource source, PlatformTransactionManager transactions,
                                      JsonMapper json, PageAdapter pages, CurrentActorProvider actors, Clock clock,
                                      org.springframework.core.env.Environment environment) {
        String owner = environment.getProperty("mom.code-rule.owner", "");
        if (!"mdm".equals(owner)) throw new IllegalStateException("mom.code-rule.owner 仅支持 mdm");
        if (!(transactions instanceof DataSourceTransactionManager manager) || manager.getDataSource() != source)
            throw new IllegalStateException("编码规则 Store 与本地事务必须使用同一 DataSource");
        return new JdbcCodeRuleStore(source, json, pages, actors, clock, owner);
    }
    @Bean CodeRuleApplication codeRuleApplication(CodeRuleStore store, CodeSegmentRegistry handlers,
                                                   CodeFieldRegistry fields, IdGenerator ids) {
        return new DefaultCodeRuleApplication(store, handlers, fields, ids);
    }
}
