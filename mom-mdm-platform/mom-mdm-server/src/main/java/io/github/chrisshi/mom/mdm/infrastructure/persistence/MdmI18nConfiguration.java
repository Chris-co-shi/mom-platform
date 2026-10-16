package io.github.chrisshi.mom.mdm.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.i18n.jdbc.JdbcI18nStore;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

/** MDM 将自有数据源、原有 ID 生成器和审计能力绑定到可选 JDBC Store。 */
@Configuration(proxyBeanMethods = false)
public class MdmI18nConfiguration {
    /**
     * 注册 MDM 唯一 I18n Store，复用 MyBatis-Plus 实际使用的 ID 生成器；不创建其他 Owner 的 Bean。
     *
     * @param dataSource 与 MDM 本地事务管理器共用的数据源，连接 Schema 为 mom_mdm
     * @param sessions MDM 既有 MyBatis-Plus 配置，仅用于取得 ASSIGN_ID 生成能力
     * @param actors 现有认证操作人提供者
     * @param clock 与其余 MDM 数据审计共用的时钟
     * @return 只访问 MDM 两张既有表的 JDBC Store
     */
    @Bean
    public I18nStore mdmI18nStore(DataSource dataSource, SqlSessionFactory sessions,
                                  CurrentActorProvider actors, Clock clock) {
        var ids = GlobalConfigUtils.getGlobalConfig(sessions.getConfiguration()).getIdentifierGenerator();
        return new JdbcI18nStore(dataSource, "mdm_i18n_message_definition", "mdm_i18n_translation",
                () -> ids.nextId(null).toString(), actors, clock);
    }
}
