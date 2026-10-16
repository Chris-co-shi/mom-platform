package io.github.chrisshi.mom.core.codesegment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owner 内编码片段的启动期校验、查找与不可变目录快照。 */
public final class CodeSegmentRegistry {
    public static final int CATALOG_VERSION = 1;
    private final Map<String, CodeSegmentHandler> handlers;
    private final List<CodeSegmentMetadata> catalog;

    public CodeSegmentRegistry(Collection<? extends CodeSegmentHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        Map<String, CodeSegmentHandler> index = new LinkedHashMap<>();
        for (CodeSegmentHandler handler : handlers) {
            Objects.requireNonNull(handler, "handler");
            CodeSegmentMetadata metadata = Objects.requireNonNull(handler.metadata(), "metadata");
            validateMetadata(metadata);
            if (index.putIfAbsent(metadata.type(), handler) != null) {
                throw new CodeSegmentException("编码片段 type 重复: " + metadata.type());
            }
        }
        this.handlers = Map.copyOf(index);
        this.catalog = index.values().stream().map(CodeSegmentHandler::metadata).toList();
    }

    public List<CodeSegmentMetadata> catalog(int version) {
        if (version != CATALOG_VERSION) throw new CodeSegmentException("不支持的 Catalog 版本: " + version);
        return catalog;
    }

    public CodeSegmentHandler require(String type) {
        CodeSegmentHandler handler = handlers.get(type);
        if (handler == null) throw new CodeSegmentException("未知编码片段 type: " + type);
        return handler;
    }

    public String render(String type, Map<String, ?> parameters, CodeSegmentRenderContext context) {
        CodeSegmentHandler handler = require(type);
        if (handler.metadata().capability() != CodeSegmentMetadata.Capability.STATELESS_RENDERABLE) {
            throw new CodeSegmentException("编码片段需要有状态引擎，不能渲染正式编号: " + type);
        }
        handler.validate(parameters);
        return handler.render(parameters, context);
    }

    private static void validateMetadata(CodeSegmentMetadata m) {
        if (m.type().isBlank() || !m.type().matches("[A-Z][A-Z0-9_]*")) throw new CodeSegmentException("非法编码片段 type");
        if (m.label().isBlank() || m.description().isBlank()) throw new CodeSegmentException("编码片段显示元数据不能为空");
        if (m.catalogVersion() != CATALOG_VERSION) throw new CodeSegmentException("不支持的 Catalog 版本: " + m.catalogVersion());
        CodeSegmentParameterSchema schema = m.parameterSchema();
        if (schema.schemaVersion() != CATALOG_VERSION) throw new CodeSegmentException("不支持的参数 Schema 版本: " + schema.schemaVersion());
        var ids = new java.util.HashSet<String>();
        var names = new java.util.HashSet<String>();
        for (var field : schema.fields()) {
            if (field.id().isBlank() || field.name().isBlank() || field.label().isBlank()) throw new CodeSegmentException("参数字段元数据不能为空");
            if (!ids.add(field.id()) || !names.add(field.name())) throw new CodeSegmentException("参数字段 id/name 必须唯一");
            if (field.type() == CodeSegmentParameterSchema.Type.SELECT && field.options().isEmpty()) throw new CodeSegmentException("SELECT 参数必须提供选项");
            if (field.type() != CodeSegmentParameterSchema.Type.SELECT && !field.options().isEmpty()) throw new CodeSegmentException("仅 SELECT 参数允许选项");
            if (field.minimum() != null && field.maximum() != null && field.minimum().doubleValue() > field.maximum().doubleValue()) throw new CodeSegmentException("参数范围无效");
            if (field.defaultValue() != null && field.type() == CodeSegmentParameterSchema.Type.SELECT
                    && field.options().stream().noneMatch(option -> option.value().equals(field.defaultValue()))) throw new CodeSegmentException("SELECT 默认值必须属于选项");
            if (field.defaultValue() != null && field.type() == CodeSegmentParameterSchema.Type.NUMBER) {
                if (!(field.defaultValue() instanceof Number number)) throw new CodeSegmentException("NUMBER 默认值必须为数字");
                double value = number.doubleValue();
                if (field.minimum() != null && value < field.minimum().doubleValue()
                        || field.maximum() != null && value > field.maximum().doubleValue()) throw new CodeSegmentException("NUMBER 默认值超出范围");
                if (field.integer() && !number.toString().matches("-?\\d+")) throw new CodeSegmentException("整数参数默认值必须为整数");
            }
        }
    }
}
