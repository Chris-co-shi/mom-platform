package io.github.chrisshi.mom.core.codesegment;

import java.util.Map;
import java.util.Objects;

/** 由服务端可信业务流程提供的编码字段上下文。 */
public record CodeGenerationContext(Map<String, String> values) {
    public CodeGenerationContext {
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    /** 获取必需的业务上下文标识。 */
    public String require(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new CodeSegmentException("缺少编码业务上下文: " + key);
        return value;
    }
}
