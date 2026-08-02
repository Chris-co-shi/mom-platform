package io.github.chrisshi.mom.idempotency;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Redis 幂等 Key 原始身份与脱敏契约测试。
 *
 * <p>测试只验证纯 Key 派生，不连接 Redis、不改变 SET-NX-TTL 命令或故障策略。Factory 是无状态且
 * 线程安全的；非法输入在任何基础设施副作用前失败，异常和派生结果均不得包含原始 Key。</p>
 */
class RedisIdempotencyKeyFactoryTest {

    private final RedisIdempotencyKeyFactory factory =
            new RedisIdempotencyKeyFactory("test", "mom-integration-server");

    /** 相同原始 Key 必须稳定产生相同摘要。 */
    @Test
    void sameRawKeyMustProduceSameProtectedKey() {
        assertEquals(factory.create("receive", "Order-001"), factory.create("receive", "Order-001"));
    }

    /** 前后空格、大小写和 Unicode 序列都属于原始身份，不得自动规范化。 */
    @Test
    void rawIdentityDifferencesMustRemainDistinct() {
        assertNotEquals(factory.create("receive", "key"), factory.create("receive", " key "));
        assertNotEquals(factory.create("receive", "Key"), factory.create("receive", "key"));

        String composed = "caf\u00e9";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        assertNotEquals(composed, decomposed);
        assertNotEquals(factory.create("receive", composed), factory.create("receive", decomposed));
    }

    /** 空值、纯空白和超长输入必须在摘要与 Redis 操作前拒绝。 */
    @Test
    void invalidRawKeysMustBeRejectedWithoutEcho() {
        assertThrows(IllegalArgumentException.class, () -> factory.create("receive", null));
        assertThrows(IllegalArgumentException.class, () -> factory.create("receive", "   "));
        IllegalArgumentException overlong = assertThrows(
                IllegalArgumentException.class,
                () -> factory.create("receive", "sensitive-value".repeat(100)));
        assertFalse(overlong.getMessage().contains("sensitive-value"));
    }

    /** 派生 Key 只能包含命名空间和固定长度摘要，不得出现原始业务值。 */
    @Test
    void protectedKeyMustNotContainRawValue() {
        String raw = " supplier-order-20260728-0001 ";
        String protectedKey = factory.create("integration.delivery.receive", raw);

        assertFalse(protectedKey.contains(raw));
        assertFalse(protectedKey.contains(raw.trim()));
        String digest = protectedKey.substring(protectedKey.lastIndexOf(':') + 1);
        assertEquals(64, digest.length());
    }
}
