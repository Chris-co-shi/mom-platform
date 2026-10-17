package io.github.chrisshi.mom.coderule.handler;

import io.github.chrisshi.mom.coderule.field.CodeFieldProvider;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentHandler;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentMetadata;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentParameterSchema;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRenderContext;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import java.util.List;
import java.util.Map;

/** 从当前 Owner 的 Provider 目录暴露业务字段选项。 */
@Component
@ConditionalOnBean(CodeFieldProvider.class)
public final class BusinessFieldCodeSegmentHandler implements CodeSegmentHandler {
    private final CodeFieldRegistry fields;
    public BusinessFieldCodeSegmentHandler(CodeFieldRegistry fields) { this.fields = fields; }
    public CodeSegmentMetadata metadata() {
        List<CodeSegmentParameterSchema.Option> options = fields.catalog().stream()
                .map(m -> new CodeSegmentParameterSchema.Option(m.label(), m.fieldKey())).toList();
        return new CodeSegmentMetadata("BUSINESS_FIELD", "业务字段", "从 Owner 业务上下文解析的字段", 1,
                new CodeSegmentParameterSchema(1, List.of(new CodeSegmentParameterSchema.Field(
                        "business_field_key", "fieldKey", CodeSegmentParameterSchema.Type.SELECT, "业务字段", true,
                        null, options, null, null, false, false))),
                CodeSegmentMetadata.Capability.STATELESS_RENDERABLE);
    }
    public void validate(Map<String, ?> parameters) {
        if (!(parameters.get("fieldKey") instanceof String key)) throw new CodeSegmentException("fieldKey 必填");
        fields.require(key);
    }
    public String render(Map<String, ?> parameters, CodeSegmentRenderContext context) {
        validate(parameters);
        return fields.require((String) parameters.get("fieldKey")).resolve(context.generationContext());
    }
}
