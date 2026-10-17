package io.github.chrisshi.mom.coderule.handler;

import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentHandler;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentMetadata;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentParameterSchema;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRenderContext;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** 固定文本片段。 */
@Component
public final class LiteralCodeSegmentHandler implements CodeSegmentHandler {
    private static final CodeSegmentMetadata META = new CodeSegmentMetadata("LITERAL", "固定文本", "固定文本编码片段", 1,
            new CodeSegmentParameterSchema(1, List.of(new CodeSegmentParameterSchema.Field("literal_value", "value",
                    CodeSegmentParameterSchema.Type.TEXT, "固定文本", true, null, List.of(), null, null, false, false))),
            CodeSegmentMetadata.Capability.STATELESS_RENDERABLE);

    public CodeSegmentMetadata metadata() { return META; }
    public void validate(Map<String, ?> parameters) {
        if (!(parameters.get("value") instanceof String value) || value.isBlank())
            throw new CodeSegmentException("value 必填且必须为非空文本");
    }
    public String render(Map<String, ?> parameters, CodeSegmentRenderContext context) {
        validate(parameters);
        return (String) parameters.get("value");
    }
}
