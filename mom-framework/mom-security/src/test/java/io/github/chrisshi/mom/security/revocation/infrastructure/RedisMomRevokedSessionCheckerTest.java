package io.github.chrisshi.mom.security.revocation.infrastructure;

import io.github.chrisshi.mom.security.revocation.MomRevocationStoreUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis revoked sid Adapter 的故障分类测试。
 *
 * <p>该测试不启动 Spring Context 或网络，只锁定超时和不确定返回值必须转换为统一脱敏异常；真实 Redis
 * 存在性和连接故障由 {@code RedisMomRevokedSessionCheckerIT} 验证。</p>
 */
class RedisMomRevokedSessionCheckerTest {

    /** Redis 命令超时不得被解释为 Session 未撤销。 */
    @Test
    void shouldFailClosedOnRedisTimeout() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey("mom:test:revoked:sid:session-1"))
                .thenThrow(new QueryTimeoutException("test timeout"));
        RedisMomRevokedSessionChecker checker =
                new RedisMomRevokedSessionChecker(redis, "mom:test:revoked:sid:");

        assertThatThrownBy(() -> checker.isRevoked("session-1"))
                .isInstanceOf(MomRevocationStoreUnavailableException.class)
                .hasMessage("revoked sid store unavailable")
                .hasRootCauseInstanceOf(QueryTimeoutException.class);
    }

    /** Redis 返回不确定结果时同样必须 Fail Closed。 */
    @Test
    void shouldFailClosedOnNullResult() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey("mom:test:revoked:sid:session-1")).thenReturn(null);
        RedisMomRevokedSessionChecker checker =
                new RedisMomRevokedSessionChecker(redis, "mom:test:revoked:sid:");

        assertThatThrownBy(() -> checker.isRevoked("session-1"))
                .isInstanceOf(MomRevocationStoreUnavailableException.class)
                .hasMessage("revoked sid store returned no result");
    }
}
