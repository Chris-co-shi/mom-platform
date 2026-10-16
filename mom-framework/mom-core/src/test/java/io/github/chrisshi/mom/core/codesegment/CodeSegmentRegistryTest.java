package io.github.chrisshi.mom.core.codesegment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodeSegmentRegistryTest {
    private CodeSegmentHandler handler(String type) {
        return new CodeSegmentHandler() {
            private final CodeSegmentMetadata metadata = new CodeSegmentMetadata(type, type, "测试", 1,
                    new CodeSegmentParameterSchema(1, List.of(new CodeSegmentParameterSchema.Field("x", "x",
                            CodeSegmentParameterSchema.Type.TEXT, "x", true, null, List.of(), null, null, false, false))),
                    CodeSegmentMetadata.Capability.STATELESS_RENDERABLE);
            @Override public CodeSegmentMetadata metadata() { return metadata; }
            @Override public void validate(Map<String, ?> parameters) { if (parameters.get("x") == null) throw new CodeSegmentException("x required"); }
            @Override public String render(Map<String, ?> parameters, CodeSegmentRenderContext context) { return parameters.get("x").toString(); }
        };
    }

    @Test void customHandlerIsIndexedAndSnapshotsAreImmutable() {
        var registry = new CodeSegmentRegistry(List.of(handler("CUSTOM")));
        assertEquals("CUSTOM", registry.catalog(1).getFirst().type());
        assertEquals("ok", registry.render("CUSTOM", Map.of("x", "ok"), new CodeSegmentRenderContext(java.time.Clock.systemUTC())));
        assertThrows(UnsupportedOperationException.class, () -> registry.catalog(1).clear());
    }

    @Test void duplicateUnknownAndUnsupportedVersionAreRejected() {
        assertThrows(CodeSegmentException.class, () -> new CodeSegmentRegistry(List.of(handler("DUP"), handler("DUP"))));
        var registry = new CodeSegmentRegistry(List.of(handler("KNOWN")));
        assertThrows(CodeSegmentException.class, () -> registry.require("MISSING"));
        assertThrows(CodeSegmentException.class, () -> registry.catalog(2));
    }
}
