package io.github.chrisshi.mom.core.codesegment;

import java.time.Clock;
import java.util.Objects;

/**
 * 将时间来源显式传入无状态片段，确保渲染可重复验证。
 */
public record CodeSegmentRenderContext(Clock clock, CodeGenerationContext generationContext) {
    /** 兼容现有仅使用时钟的片段调用。 */
    public CodeSegmentRenderContext(Clock clock) {
        this(clock, new CodeGenerationContext(java.util.Map.of()));
    }

    public CodeSegmentRenderContext {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(generationContext, "generationContext");
    }
}
