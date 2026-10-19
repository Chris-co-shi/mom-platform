package io.github.chrisshi.mom.i18n.autoconfigure;

import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.runtime.LocalBaseLocalePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import io.github.chrisshi.mom.i18n.store.JdbcI18nStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;

/** 按显式 Owner 将当前服务既有 DataSource 和技术能力绑定到公共 JDBC Store。 */
@AutoConfiguration(afterName = "io.github.chrisshi.mom.data.config.MomDataAutoConfiguration")
@ConditionalOnProperty(prefix = "mom.i18n", name = "owner")
public class I18nJdbcAutoConfiguration {
    private static final Map<String, String> TABLE_PREFIXES = Map.of(
            "auth", "auth", "mdm", "mdm", "system", "system", "wms", "wms");

    /** 只在显式 Owner 且宿主未提供 Store 时创建本地 JDBC 适配器。 */
    @Bean
    @ConditionalOnMissingBean(I18nStore.class)
    I18nStore i18nStore(Environment environment, DataSource dataSource,
                        PlatformTransactionManager transactionManager,
                        IdGenerator ids, CurrentActorProvider actors, Clock clock) {
        String owner = environment.getProperty("mom.i18n.owner", "").toLowerCase(Locale.ROOT);
        String prefix = TABLE_PREFIXES.get(owner);
        if (prefix == null) {
            throw new IllegalStateException("mom.i18n.owner 仅支持 auth、mdm、system 或 wms");
        }
        if (!(transactionManager instanceof DataSourceTransactionManager manager)) {
            throw new IllegalStateException("I18n JDBC Store 要求宿主提供 DataSourceTransactionManager");
        }
        if (manager.getDataSource() != dataSource) {
            throw new IllegalStateException("I18n JDBC Store 与本地事务管理器必须使用同一 DataSource");
        }
        return new JdbcI18nStore(dataSource, prefix + "_i18n_message_definition",
                prefix + "_i18n_translation", ids::nextId, actors, clock);
    }

    /** 非 System Owner 使用固定内部缺失译文兜底，不把它当作平台动态默认语言。 */
    @Bean
    @ConditionalOnMissingBean(I18nLocalePolicy.class)
    I18nLocalePolicy i18nLocalePolicy() {
        return new LocalBaseLocalePolicy("zh-CN");
    }
}
