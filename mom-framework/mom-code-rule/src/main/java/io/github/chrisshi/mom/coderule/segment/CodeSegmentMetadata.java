package io.github.chrisshi.mom.coderule.segment;

import java.util.Objects;

/**
 * 编码片段稳定目录元数据。
 */
public record CodeSegmentMetadata(String type, String label, String description, int catalogVersion,
                                  CodeSegmentParameterSchema parameterSchema, Capability capability) {
    public CodeSegmentMetadata {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(parameterSchema, "parameterSchema");
        Objects.requireNonNull(capability, "capability");
    }

    public enum Capability {STATELESS_RENDERABLE, STATEFUL_ENGINE_REQUIRED}
}
