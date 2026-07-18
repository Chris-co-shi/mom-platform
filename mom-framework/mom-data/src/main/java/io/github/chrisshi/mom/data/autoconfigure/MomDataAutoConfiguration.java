package io.github.chrisshi.mom.data.autoconfigure;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.audit.UtcAuditMetaObjectHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * MOM 平台关系型数据访问自动配置。
 *
 * <p>该配置在 MyBatis-Plus 官方自动配置之前注册拦截器和审计处理器，使官方
 * {@code MybatisPlusAutoConfiguration} 创建 {@code SqlSessionFactory} 时能够发现这些 Bean。
 * 模块只提供通用基础设施，不扫描特定领域 Mapper，也不持有任何业务表迁移。</p>
 */
@AutoConfiguration(beforeName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MomDataAutoConfiguration {

    /**
     * 提供平台统一 UTC 时钟。
     *
     * <p>应用或测试可以声明自定义 {@link Clock} 覆盖默认实现。统一时钟可以避免实体审计依赖服务器
     * 默认时区，并使时间相关测试能够使用固定时钟稳定复现。</p>
     *
     * @return UTC 系统时钟
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock momUtcClock() {
        return Clock.systemUTC();
    }

    /**
     * 创建审计字段自动填充器。
     *
     * @param clock 平台时钟
     * @param actorProviders 可选的当前操作主体提供器
     * @return MyBatis-Plus MetaObjectHandler
     */
    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    MetaObjectHandler momAuditMetaObjectHandler(
            Clock clock,
            ObjectProvider<CurrentActorProvider> actorProviders) {
        return new UtcAuditMetaObjectHandler(clock, actorProviders);
    }

    /**
     * 创建 MyBatis-Plus 通用拦截器链。
     *
     * <p>当前只启用乐观锁。分页、数据权限和防全表更新等能力需要独立评审后按明确顺序加入，避免
     * 在基础切片中形成不可见的 SQL 改写组合。</p>
     *
     * @return 包含乐观锁内部拦截器的 MybatisPlusInterceptor
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    MybatisPlusInterceptor momMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
