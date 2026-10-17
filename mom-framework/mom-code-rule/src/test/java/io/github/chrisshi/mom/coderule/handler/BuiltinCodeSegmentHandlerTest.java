package io.github.chrisshi.mom.coderule.handler;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuiltinCodeSegmentHandlerTest {
    private List<io.github.chrisshi.mom.coderule.segment.CodeSegmentHandler> handlers() {
        return List.of(new io.github.chrisshi.mom.coderule.handler.LiteralCodeSegmentHandler(),
                new io.github.chrisshi.mom.coderule.handler.DateCodeSegmentHandler(),
                new io.github.chrisshi.mom.coderule.handler.SequenceCodeSegmentHandler());
    }

    @Test void builtinsValidateRenderAndNeverFakeSequenceResult() {
        var registry = new io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry(handlers());
        var context = new io.github.chrisshi.mom.coderule.segment.CodeSegmentRenderContext(
                Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC));
        assertEquals("固定", registry.render("LITERAL", Map.of("value", "固定"), context));
        assertEquals("20260304", registry.render("DATE", Map.of("format", "YYYYMMDD"), context));
        assertEquals("2603", registry.render("DATE", Map.of("format", "YYMM"), context));
        var sequence = registry.require("SEQUENCE");
        sequence.validate(Map.of("width", 4));
        sequence.validate(Map.of("width", 4, "step", 1));
        assertThrows(IllegalArgumentException.class, () -> sequence.validate(Map.of("width", 4.5, "step", 1)));
        assertThrows(IllegalArgumentException.class, () -> sequence.validate(Map.of("width", 19, "step", 1)));
        assertThrows(IllegalArgumentException.class, () -> sequence.validate(Map.of("width", 4, "step", 2)));
        assertThrows(IllegalArgumentException.class, () -> registry.render("SEQUENCE", Map.of("width", 4, "step", 1), context));
        assertThrows(IllegalArgumentException.class, () -> registry.require("DATE").validate(Map.of("format", "YYYY")));
        assertThrows(IllegalArgumentException.class, () -> registry.require("LITERAL").validate(Map.of()));
    }

    @Test void sequenceSchemaDeclaresIntegerBoundsAndReadOnlyStep() {
        var sequence = handlers().stream().filter(h -> h.metadata().type().equals("SEQUENCE")).findFirst().orElseThrow();
        var fields = sequence.metadata().parameterSchema().fields();
        assertEquals(1, fields.getFirst().minimum());
        assertEquals(18, fields.getFirst().maximum());
        assertTrue(fields.getFirst().integer());
        assertTrue(fields.get(1).readOnly());
    }
}
