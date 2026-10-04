package io.github.chrisshi.mom.data.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.audit.MomMetaObjectHandler;
import io.github.chrisshi.mom.data.page.PageAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.Optional;

/**
 * MOM 关系型数据访问、审计和乐观锁自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(MomDataPaginationProperties.class)
public class MomDataAutoConfiguration {

    /**
     * 提供可被测试替换的 UTC 时钟。
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock momUtcClock() {
        return Clock.systemUTC();
    }

    /**
     * 创建服务端受控的审计处理器。
     */
    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    MetaObjectHandler momMetaObjectHandler(
        Clock clock,
        ObjectProvider<CurrentActorProvider> providers) {
        CurrentActorProvider actorProvider =
            providers.getIfAvailable(() -> Optional::empty);
        return new MomMetaObjectHandler(clock, actorProvider);
    }

    /**
     * 没有应用自定义链时提供空链，乐观锁由后处理器追加。
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    MybatisPlusInterceptor momMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor =
            new MybatisPlusInterceptor();
        // 乐观锁
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 防止全表删除
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    /**
     * 创建在 SQL 执行前拒绝超大分页请求的统一适配器。
     *
     * @param paginationProperties 启动时完成绑定的分页保护配置
     * @return 不可变、线程安全的分页适配器
     */
    @Bean
    @ConditionalOnMissingBean(PageAdapter.class)
    PageAdapter momPageAdapter(MomDataPaginationProperties paginationProperties) {
        return new PageAdapter(paginationProperties.getMaxPageSize());
    }
}
