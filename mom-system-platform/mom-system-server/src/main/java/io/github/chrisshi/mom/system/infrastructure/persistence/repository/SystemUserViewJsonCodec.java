package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import io.github.chrisshi.mom.system.application.preference.PreferencePayloadSizer;
import io.github.chrisshi.mom.system.domain.preference.ColumnSetting;
import io.github.chrisshi.mom.system.domain.preference.FilterSetting;
import io.github.chrisshi.mom.system.domain.preference.SortSetting;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 视图类型模型与 JSONB String 的受控 Codec。
 *
 * <p>只序列化/反序列化三个明确 Record 数组，不接受任意 Object 进入 Domain。相同 Codec 同时计算 16 KiB
 * 门禁和生成持久化值；数据库损坏时 fail closed，不返回部分设置或默认伪成功。</p>
 */
@Component
public class SystemUserViewJsonCodec implements PreferencePayloadSizer {
    private final ObjectMapper objectMapper;

    public SystemUserViewJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int encodedBytes(List<ColumnSetting> columns, List<SortSetting> sorts, List<FilterSetting> filters) {
        return encodeColumns(columns).getBytes(StandardCharsets.UTF_8).length
                + encodeSorts(sorts).getBytes(StandardCharsets.UTF_8).length
                + encodeFilters(filters).getBytes(StandardCharsets.UTF_8).length;
    }

    String encodeColumns(List<ColumnSetting> values) {
        return write(values);
    }

    String encodeSorts(List<SortSetting> values) {
        return write(values);
    }

    String encodeFilters(List<FilterSetting> values) {
        return write(values);
    }

    List<ColumnSetting> decodeColumns(String json) {
        return readList(json, ColumnSetting[].class);
    }

    List<SortSetting> decodeSorts(String json) {
        return readList(json, SortSetting[].class);
    }

    List<FilterSetting> decodeFilters(String json) {
        return readList(json, FilterSetting[].class);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法编码受控视图设置", exception);
        }
    }

    private <T> List<T> readList(String json, Class<T[]> type) {
        try {
            T[] values = objectMapper.readValue(json, type);
            return List.of(values);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("持久化视图设置结构损坏", exception);
        }
    }
}
