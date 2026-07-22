package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Redis 撤销状态不可读取时不得把 sid 视为有效。 */
class IamRevokedSessionStoreTest {
    @Test
    void redisFailureMustFailClosed() {
        StringRedisTemplate unavailableRedis = new StringRedisTemplate() {
            @Override
            public Boolean hasKey(String key) {
                throw new RedisConnectionFailureException("test redis unavailable");
            }
        };
        IamSessionProperties properties = new IamSessionProperties();
        IamRevokedSessionStore store = new IamRevokedSessionStore(
                unavailableRedis, properties, Clock.systemUTC());

        assertThrows(
                IamRevokedSessionStore.RevocationStoreUnavailableException.class,
                () -> store.isRevoked("123456789"));
    }
}
