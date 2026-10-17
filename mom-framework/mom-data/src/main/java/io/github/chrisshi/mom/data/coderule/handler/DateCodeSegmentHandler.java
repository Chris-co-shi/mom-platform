package io.github.chrisshi.mom.data.coderule.handler;

import io.github.chrisshi.mom.core.codesegment.*;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 使用显式时钟的日期片段。 */
@Component
public final class DateCodeSegmentHandler implements CodeSegmentHandler {
    private static final List<String> FORMATS = List.of("YYYYMMDD", "YYMMDD", "YYYYMM", "YYMM");
    private static final CodeSegmentMetadata META = new CodeSegmentMetadata("DATE", "日期", "日期编码片段", 1,
            new CodeSegmentParameterSchema(1, List.of(new CodeSegmentParameterSchema.Field("date_format", "format",
                    CodeSegmentParameterSchema.Type.SELECT, "日期格式", true, "YYYYMMDD",
                    FORMATS.stream().map(v -> new CodeSegmentParameterSchema.Option(v, v)).toList(),
                    null, null, false, false))), CodeSegmentMetadata.Capability.STATELESS_RENDERABLE);

    public CodeSegmentMetadata metadata() { return META; }
    public void validate(Map<String, ?> parameters) {
        if (!FORMATS.contains(parameters.get("format"))) throw new CodeSegmentException("format 必须为支持的选项");
    }
    public String render(Map<String, ?> parameters, CodeSegmentRenderContext context) {
        validate(parameters);
        String pattern = ((String) parameters.get("format")).replace("YYYY", "uuuu").replace("YY", "yy")
                .replace("DD", "dd");
        return DateTimeFormatter.ofPattern(pattern).format(context.clock().instant().atZone(context.clock().getZone()));
    }
}
