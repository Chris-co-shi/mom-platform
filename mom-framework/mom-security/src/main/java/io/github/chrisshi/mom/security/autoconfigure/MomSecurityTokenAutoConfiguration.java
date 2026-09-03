package io.github.chrisshi.mom.security.autoconfigure;

import io.github.chrisshi.mom.security.token.MomTokenStore;
import io.github.chrisshi.mom.security.token.RedisMomTokenStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * MOM 令牌存储自动装配。
 *
 * <p>当 classpath 中存在 {@link StringRedisTemplate} 和 {@link JsonMapper} 时，
 * 自动注册基于 Redis 的 {@link MomTokenStore} 实现。
 * 若应用已自行定义 {@link MomTokenStore} Bean，则本装配不生效。</p>
 *
 * @author 史偕成
 * @since 2026-09-03
 */
@AutoConfiguration
@ConditionalOnClass({
    StringRedisTemplate.class,
    JsonMapper.class
})
public class MomSecurityTokenAutoConfiguration {

    /**
     * 创建基于 Redis 的令牌存储实例。
     *
     * <p>仅在容器中不存在 {@link MomTokenStore} Bean 且
     * {@link StringRedisTemplate}、{@link JsonMapper} 均可用时生效。</p>
     *
     * @param redisTemplate Redis 字符串操作模板，用于读写令牌认证快照
     * @param jsonMapper    JSON 序列化器，用于令牌 Principal 的序列化与反序列化
     * @param clockProvider 时钟提供者，用于计算令牌剩余有效期；未提供时默认使用 UTC 时钟
     * @return 基于 Redis 的 {@link MomTokenStore} 实现
     */
    @Bean
    @ConditionalOnMissingBean(MomTokenStore.class)
    @ConditionalOnBean({
        StringRedisTemplate.class,
        JsonMapper.class
    })
    MomTokenStore momTokenStore(
        StringRedisTemplate redisTemplate,
        JsonMapper jsonMapper,
        ObjectProvider<Clock> clockProvider
    ) {
        // 优先使用应用自定义的 Clock，未提供时回退到 UTC
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);

        return new RedisMomTokenStore(
            redisTemplate,
            jsonMapper,
            clock
        );
    }
}
