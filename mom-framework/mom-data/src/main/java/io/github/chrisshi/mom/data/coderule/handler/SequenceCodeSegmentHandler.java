package io.github.chrisshi.mom.data.coderule.handler;

import io.github.chrisshi.mom.core.codesegment.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 仅描述流水号参数，不分配编号。 */
@Component
public final class SequenceCodeSegmentHandler implements CodeSegmentHandler {
    private static final CodeSegmentMetadata META = new CodeSegmentMetadata("SEQUENCE", "流水号", "需要事务型计数器的流水号片段", 1,
            new CodeSegmentParameterSchema(1, List.of(
                    new CodeSegmentParameterSchema.Field("sequence_width", "width", CodeSegmentParameterSchema.Type.NUMBER,
                            "流水号宽度", true, 4, List.of(), 1, 18, false, true),
                    new CodeSegmentParameterSchema.Field("sequence_step", "step", CodeSegmentParameterSchema.Type.NUMBER,
                            "步长", true, 1, List.of(), 1, 1, true, true))),
            CodeSegmentMetadata.Capability.STATEFUL_ENGINE_REQUIRED);

    public CodeSegmentMetadata metadata() { return META; }
    public void validate(Map<String, ?> parameters) {
        if (!integerInRange(parameters.get("width"), 1, 18)) throw new CodeSegmentException("width 必须是 1–18 的整数");
        Object step = parameters.get("step");
        if (step != null && !integerInRange(step, 1, 1)) throw new CodeSegmentException("step 必须等于 1");
    }
    private static boolean integerInRange(Object value, int min, int max) {
        if (!(value instanceof Number n)) return false;
        try {
            BigDecimal decimal = new BigDecimal(n.toString());
            return decimal.stripTrailingZeros().scale() <= 0 && decimal.compareTo(BigDecimal.valueOf(min)) >= 0
                    && decimal.compareTo(BigDecimal.valueOf(max)) <= 0;
        } catch (NumberFormatException ex) { return false; }
    }
}
