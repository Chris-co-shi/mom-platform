package io.github.chrisshi.mom.system.application.preference;

import io.github.chrisshi.mom.system.domain.preference.ColumnSetting;
import io.github.chrisshi.mom.system.domain.preference.FilterSetting;
import io.github.chrisshi.mom.system.domain.preference.SortSetting;

import java.util.List;

/**
 * 类型化视图设置的受控序列化大小 Port。
 *
 * <p>Application 仅获取 UTF-8 字节数，不接触 JSON String；Infrastructure 使用同一 Jackson Codec
 * 计算持久化 Payload 大小，保证 16 KiB 门禁与实际 JSONB 一致。</p>
 */
public interface PreferencePayloadSizer {
    int encodedBytes(List<ColumnSetting> columns, List<SortSetting> sorts, List<FilterSetting> filters);
}
