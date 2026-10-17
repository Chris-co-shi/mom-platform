package io.github.chrisshi.mom.coderule.field;

import java.util.List;

/** Owner 可提供的业务字段目录信息。 */
public record CodeFieldMetadata(String fieldKey, String label, String description,
                                List<String> requiredContextKeys) {
    public CodeFieldMetadata {
        requiredContextKeys = List.copyOf(requiredContextKeys);
    }
}
