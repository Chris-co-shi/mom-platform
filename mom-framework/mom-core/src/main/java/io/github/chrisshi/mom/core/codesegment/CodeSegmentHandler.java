package io.github.chrisshi.mom.core.codesegment;

import java.util.Map;

/**
 * 描述由一个 Owner 注册的编码片段及其可选的确定性渲染能力。
 */
public interface CodeSegmentHandler {
    CodeSegmentMetadata metadata();

    void validate(Map<String, ?> parameters);

    /**
     * 仅 STATELESS_RENDERABLE 类型实现；不提供正式编号能力。
     */
    default String render(Map<String, ?> parameters, CodeSegmentRenderContext context) {
        throw new UnsupportedOperationException("该编码片段不支持无状态渲染");
    }
}
