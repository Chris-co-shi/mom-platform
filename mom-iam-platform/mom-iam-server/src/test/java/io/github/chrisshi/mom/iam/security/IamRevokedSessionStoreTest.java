package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.security.revocation.MomRevocationStoreUnavailableException;
import io.github.chrisshi.mom.security.revocation.MomRevokedSessionKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                MomRevocationStoreUnavailableException.class,
                () -> store.isRevoked("123456789"));
    }

    @Test
    void revokeMustUseSharedSecurityKeyAndBoundedTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        IamSessionProperties properties = new IamSessionProperties();
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        IamRevokedSessionStore store = new IamRevokedSessionStore(
                redis, properties, Clock.fixed(now, ZoneOffset.UTC));

        store.revoke("123456789", now.plusSeconds(60));

        verify(values).set(
                MomRevokedSessionKeys.key(properties.getRevokedKeyPrefix(), "123456789"),
                "1",
                Duration.ofSeconds(60));
    }
}
