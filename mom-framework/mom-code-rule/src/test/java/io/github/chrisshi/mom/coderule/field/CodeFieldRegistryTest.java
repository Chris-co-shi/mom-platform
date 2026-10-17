package io.github.chrisshi.mom.coderule.field;

import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Provider 注册边界的行为测试。 */
class CodeFieldRegistryTest {
    private static CodeFieldProvider provider(String key) {
        return new CodeFieldProvider() {
            public CodeFieldMetadata metadata() { return new CodeFieldMetadata(key, "字段", "说明", List.of("entityId")); }
            public String resolve(CodeGenerationContext context) { return context.require("entityId"); }
        };
    }
    @Test void autoIndexRejectsDuplicateAndInvalidFieldKeys() {
        assertThrows(CodeSegmentException.class, () -> new CodeFieldRegistry(List.of(provider("a.b"), provider("a.b"))));
        assertThrows(CodeSegmentException.class, () -> new CodeFieldRegistry(List.of(provider("a b"))));
        assertThrows(CodeSegmentException.class, () -> new CodeFieldRegistry(List.of(provider(""))));
        assertThrows(CodeSegmentException.class, () -> new CodeFieldRegistry(List.of(provider("bad."))));
        var registry = new CodeFieldRegistry(List.of(provider("z.code"), provider("a.code")));
        assertEquals(List.of("a.code", "z.code"), registry.catalog().stream().map(CodeFieldMetadata::fieldKey).toList());
        assertEquals("1", registry.require("a.code").resolve(new CodeGenerationContext(java.util.Map.of("entityId", "1"))));
        assertThrows(CodeSegmentException.class, () -> registry.require("missing"));
    }
}
