package io.github.chrisshi.mom.system.infrastructure.cache;

import io.github.chrisshi.mom.cache.api.CacheEntryKey;
import io.github.chrisshi.mom.cache.api.CacheRegion;
import io.github.chrisshi.mom.cache.api.CacheScope;
import io.github.chrisshi.mom.cache.api.CacheService;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeQueryPort;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** System Cache Adapter 必须使用 Global typed Region，且失效不得扫描 Redis。 */
class MomCacheSystemRuntimeCacheAdapterTest {

    @Test
    void catalogMustUseVersionedGlobalTypedKey() {
        CacheService cacheService = mock(CacheService.class);
        SystemCatalogSnapshot snapshot = mock(SystemCatalogSnapshot.class);
        when(cacheService.get(any())).thenReturn(snapshot);
        MomCacheSystemRuntimeCacheAdapter adapter =
                new MomCacheSystemRuntimeCacheAdapter(cacheService, true);

        assertThat(adapter.findCatalog("mes", 7, "a".repeat(64))).contains(snapshot);

        ArgumentCaptor<CacheEntryKey<?>> key = cacheKeyCaptor();
        verify(cacheService).get(key.capture());
        assertThat(key.getValue().scope()).isEqualTo(CacheScope.global());
        assertThat(key.getValue().region().capability()).isEqualTo("catalog-release");
        assertThat(key.getValue().subject()).isEqualTo("mes:7:" + "a".repeat(64));
    }

    @Test
    void invalidationMustClearOnlyBoundedLocalRegions() {
        CacheService cacheService = mock(CacheService.class);
        MomCacheSystemRuntimeCacheAdapter adapter =
                new MomCacheSystemRuntimeCacheAdapter(cacheService, true);

        adapter.evictDictionary("material-type");

        ArgumentCaptor<CacheRegion<?>> region = cacheRegionCaptor();
        verify(cacheService, org.mockito.Mockito.times(2)).invalidateLocalRegion(region.capture());
        assertThat(region.getAllValues())
                .extracting(CacheRegion::capability)
                .containsExactlyInAnyOrder("dictionary-active", "dictionary-item");
    }

    @Test
    void i18nMustRejectSnapshotThatDoesNotMatchAuthoritativeHeader() {
        CacheService cacheService = mock(CacheService.class);
        MomCacheSystemI18nRuntimeCacheAdapter adapter =
                new MomCacheSystemI18nRuntimeCacheAdapter(cacheService, true);
        Instant publishedAt = Instant.parse("2026-08-01T00:00:00Z");
        SystemI18nRuntimeQueryPort.RuntimeHeader header = new SystemI18nRuntimeQueryPort.RuntimeHeader(
                "1", "mes", "common", "zh-CN", 3, "en-US", "b".repeat(64), 0, publishedAt);
        SystemI18nRuntimeQueryPort.RuntimeSnapshot wrong = new SystemI18nRuntimeQueryPort.RuntimeSnapshot(
                "mes", "common", "en-US", "zh-CN", 4, "b".repeat(64), 0, publishedAt, Map.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.put(header, wrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Header 不一致");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<CacheEntryKey<?>> cacheKeyCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(CacheEntryKey.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<CacheRegion<?>> cacheRegionCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(CacheRegion.class);
    }
}
