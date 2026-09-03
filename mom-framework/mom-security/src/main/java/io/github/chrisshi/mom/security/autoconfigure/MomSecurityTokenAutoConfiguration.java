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
 * @author 史偕成
 * @date 2026/09/03 10:30
 **/
@AutoConfiguration
@ConditionalOnClass({
    StringRedisTemplate.class,
    JsonMapper.class
})
public class MomSecurityTokenAutoConfiguration {

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
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);

        return new RedisMomTokenStore(
            redisTemplate,
            jsonMapper,
            clock
        );
    }
}
