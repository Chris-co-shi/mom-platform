package io.github.chrisshi.mom.coderule.field;

import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 启动期验证 Provider 并提供不可变字段目录。 */
public final class CodeFieldRegistry {
    private final Map<String, CodeFieldProvider> providers;
    private final List<CodeFieldMetadata> catalog;

    public CodeFieldRegistry(Collection<? extends CodeFieldProvider> beans) {
        Map<String, CodeFieldProvider> index = new LinkedHashMap<>();
        for (CodeFieldProvider provider : Objects.requireNonNull(beans, "beans")) {
            CodeFieldMetadata metadata = Objects.requireNonNull(provider.metadata(), "metadata");
            String key = metadata.fieldKey();
            if (key == null || key.length() > 128
                    || !key.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")
                    || metadata.label() == null || metadata.label().isBlank()
                    || metadata.description() == null || metadata.description().isBlank()
                    || metadata.requiredContextKeys() == null
                    || metadata.requiredContextKeys().stream().anyMatch(k -> k == null || !k.matches("[A-Za-z][A-Za-z0-9_]{0,63}"))
                    || metadata.requiredContextKeys().stream().distinct().count() != metadata.requiredContextKeys().size()) {
                throw new CodeSegmentException("非法编码字段元数据");
            }
            if (index.putIfAbsent(key, provider) != null) throw new CodeSegmentException("编码字段 fieldKey 重复: " + key);
        }
        providers = Map.copyOf(index);
        catalog = index.values().stream().map(CodeFieldProvider::metadata)
                .sorted(java.util.Comparator.comparing(CodeFieldMetadata::fieldKey)).toList();
    }

    /** 返回按稳定 fieldKey 排序的字段目录。 */
    public List<CodeFieldMetadata> catalog() {
        return catalog;
    }

    /** 要求当前 Owner 具备该字段能力。 */
    public CodeFieldProvider require(String fieldKey) {
        CodeFieldProvider provider = providers.get(fieldKey);
        if (provider == null) throw new CodeSegmentException("不支持的 fieldKey: " + fieldKey);
        return provider;
    }
}
