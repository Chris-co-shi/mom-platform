package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Catalog Snapshot 与 JSONB String 的受控 Jackson 3 Codec。
 *
 * <p>只处理明确的不可变 Snapshot Record；不接受 Web 任意 JSON。编码或持久化结构损坏时 Fail Closed，
 * 不返回部分目录或静态伪成功。</p>
 */
@Component
public class SystemCatalogJsonCodec implements SystemCatalogSnapshotCodec {
    private final ObjectMapper objectMapper;

    public SystemCatalogJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String encode(SystemCatalogSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法编码 Catalog Snapshot", exception);
        }
    }

    @Override
    public SystemCatalogSnapshot decode(String json) {
        try {
            return objectMapper.readValue(json, SystemCatalogSnapshot.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("持久化 Catalog Snapshot 结构损坏", exception);
        }
    }
}
