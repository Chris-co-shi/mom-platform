package io.github.chrisshi.mom.mdm.codesegment;

import io.github.chrisshi.mom.core.codesegment.CodeSegmentException;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentHandler;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentMetadata;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentParameterSchema;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentParameterSchema.Field;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentParameterSchema.Option;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentRenderContext;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MDM Owner 的 V1 编码片段 Spring Bean 注册。 */
@Configuration(proxyBeanMethods = false)
public class MdmCodeSegmentHandlers {
    private static final CodeSegmentMetadata.Capability RENDERABLE = CodeSegmentMetadata.Capability.STATELESS_RENDERABLE;
    private static final CodeSegmentMetadata.Capability STATEFUL = CodeSegmentMetadata.Capability.STATEFUL_ENGINE_REQUIRED;

    @Bean CodeSegmentHandler literalCodeSegment() {
        var metadata = metadata("LITERAL", "固定文本", "固定文本编码片段", RENDERABLE,
                List.of(field("literal_value", "value", CodeSegmentParameterSchema.Type.TEXT, "固定文本", true, null)));
        return new BaseHandler(metadata) {
            @Override public void validate(Map<String, ?> p) { requiredText(p, "value"); }
            @Override public String render(Map<String, ?> p, CodeSegmentRenderContext c) { validate(p); return (String) p.get("value"); }
        };
    }

    @Bean CodeSegmentHandler dateCodeSegment() {
        List<String> formats = List.of("YYYYMMDD", "YYMMDD", "YYYYMM", "YYMM");
        var field = new Field("date_format", "format", CodeSegmentParameterSchema.Type.SELECT, "日期格式", true,
                "YYYYMMDD", formats.stream().map(v -> new Option(v, v)).toList(), null, null, false, false);
        var metadata = metadata("DATE", "日期", "日期编码片段", RENDERABLE, List.of(field));
        return new BaseHandler(metadata) {
            @Override public void validate(Map<String, ?> p) { requiredChoice(p, "format", formats); }
            @Override public String render(Map<String, ?> p, CodeSegmentRenderContext c) {
                validate(p);
                String pattern = ((String) p.get("format")).replace("YYYY", "uuuu").replace("YY", "yy").replace("MM", "MM").replace("DD", "dd");
                return DateTimeFormatter.ofPattern(pattern).format(c.clock().instant().atZone(c.clock().getZone()));
            }
        };
    }

    @Bean CodeSegmentHandler sequenceCodeSegment() {
        var width = new Field("sequence_width", "width", CodeSegmentParameterSchema.Type.NUMBER, "流水号宽度", true, 4, List.of(), 1, 18, false, true);
        var step = new Field("sequence_step", "step", CodeSegmentParameterSchema.Type.NUMBER, "步长", true, 1, List.of(), 1, 1, true, true);
        var metadata = metadata("SEQUENCE", "流水号", "需要事务型计数器的流水号片段", STATEFUL, List.of(width, step));
        return new BaseHandler(metadata) {
            @Override public void validate(Map<String, ?> p) {
                Object w = p.get("width");
                if (!(w instanceof Number n) || !isInteger(n) || n.intValue() < 1 || n.intValue() > 18) throw new CodeSegmentException("width 必须是 1–18 的整数");
                Object s = p.get("step");
                if (s != null && (!(s instanceof Number n) || !isInteger(n) || n.intValue() != 1)) throw new CodeSegmentException("step 必须等于 1");
            }
        };
    }

    private static CodeSegmentMetadata metadata(String type, String label, String description,
            CodeSegmentMetadata.Capability capability, List<Field> fields) {
        return new CodeSegmentMetadata(type, label, description, 1, new CodeSegmentParameterSchema(1, fields), capability);
    }
    private static Field field(String id, String name, CodeSegmentParameterSchema.Type type, String label, boolean required, Object defaultValue) {
        return new Field(id, name, type, label, required, defaultValue, List.of(), null, null, false, false);
    }
    private abstract static class BaseHandler implements CodeSegmentHandler {
        private final CodeSegmentMetadata metadata;
        private BaseHandler(CodeSegmentMetadata metadata) { this.metadata = metadata; }
        @Override public CodeSegmentMetadata metadata() { return metadata; }
    }
    private static void requiredText(Map<String, ?> p, String key) {
        Object value = p.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new CodeSegmentException(key + " 必填且必须为非空文本");
    }
    private static void requiredChoice(Map<String, ?> p, String key, List<String> options) {
        Object value = p.get(key);
        if (!(value instanceof String text) || !options.contains(text)) throw new CodeSegmentException(key + " 必须为支持的选项");
    }
    private static boolean isInteger(Number value) {
        try { return new java.math.BigDecimal(value.toString()).stripTrailingZeros().scale() <= 0; }
        catch (NumberFormatException ex) { return false; }
    }
}
