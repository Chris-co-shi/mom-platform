package io.github.chrisshi.mom.data.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.audit.MomMetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * MOM 关系型数据访问、审计和乐观锁自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
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
        CurrentActorProvider actorProvider) {
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
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        // 最大分页数量
        pagination.setMaxLimit(200L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
