package io.github.chrisshi.mom.cache.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 刻画 P1.6 Cache 公共 API 的兼容边界与新 Key 隔离规则。
 *
 * <p>该测试位于 Framework 契约层，不连接 Redis 或业务模块。它保证旧入口只能被弃用而不能在本轮删除，
 * 同时验证新 Region API 不允许弱类型反序列化或把最终授权决策伪装成普通缓存值。</p>
 */
class CacheApiCompatibilityTest {

    private static final Set<String> LEGACY_CACHE_TYPES = Set.of(
            "IAM_PERMISSION",
            "SYSTEM_DICTIONARY",
            "SYSTEM_PARAMETER",
            "SYSTEM_I18N",
            "USER_SESSION"
    );

    @Test
    void shouldKeepLegacyApiDeprecatedUntilRemovalAdrAccepted() throws NoSuchMethodException {
        assertThat(CacheType.class).hasAnnotation(Deprecated.class);
        assertThat(CacheKey.class).hasAnnotation(Deprecated.class);
        assertThat(CachePolicy.class).hasAnnotation(Deprecated.class);

        Method get = CacheService.class.getMethod("get", CacheKey.class, Class.class);
        Method put = CacheService.class.getMethod("put", CacheKey.class, Object.class);
        Method evict = CacheService.class.getMethod("evict", CacheKey.class);

        assertThat(get.isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(put.isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(evict.isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(Arrays.stream(CacheType.values()).map(Enum::name).toList())
                .containsExactlyInAnyOrderElementsOf(LEGACY_CACHE_TYPES);
    }

    @Test
    void shouldBuildIsolatedGlobalAndFactoryKeys() {
        CacheRegion<ExampleValue> region = new CacheRegion<>(
                "system",
                "dictionary",
                1,
                CacheValueType.of("system.dictionary", 1, ExampleValue.class),
                Duration.ofMinutes(1),
                Duration.ofMinutes(10),
                true,
                true
        );

        CacheEntryKey<ExampleValue> global = CacheEntryKey.of(
                region,
                CacheScope.global(),
                "material-type"
        );
        CacheEntryKey<ExampleValue> factory = CacheEntryKey.of(
                region,
                CacheScope.factory("factory01"),
                "material-type"
        );

        assertThat(global.build("prod"))
                .isEqualTo("mom:prod:_global:system:cache:v1:dictionary:material-type");
        assertThat(factory.build("prod"))
                .isEqualTo("mom:prod:factory01:system:cache:v1:dictionary:material-type");
        assertThat(factory.build("prod")).isNotEqualTo(global.build("prod"));
    }

    @Test
    void shouldRejectReservedOrUnvalidatedFactoryScope() {
        assertThatIllegalArgumentException().isThrownBy(() -> CacheScope.factory("_global"));
        assertThatIllegalArgumentException().isThrownBy(() -> CacheScope.factory(" factory01"));
        assertThatIllegalArgumentException().isThrownBy(() -> CacheScope.factory("factory:01"));
    }

    @Test
    void shouldRejectWeakOrAuthorizationDecisionValueTypes() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> CacheValueType.of("generic.object", 1, Object.class));
        assertThatIllegalArgumentException().isThrownBy(
                () -> CacheValueType.of("authorization-decision", 1, AuthorizationDecision.class));
        assertThatIllegalArgumentException().isThrownBy(
                () -> CacheValueType.of("permission-evaluation-result", 1, PermissionEvaluationResult.class));
    }

    private record ExampleValue(String code) {
    }

    private record AuthorizationDecision(boolean allowed) {
    }

    private record PermissionEvaluationResult(boolean allowed) {
    }
}
