package io.github.chrisshi.mom.coderule.segment;

import java.util.List;
import java.util.Objects;

/**
 * 前端可解析的参数表单 Schema V1。
 */
public record CodeSegmentParameterSchema(int schemaVersion, List<Field> fields) {
    public CodeSegmentParameterSchema {
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    }

    public record Field(String id, String name, Type type, String label, boolean required,
                        Object defaultValue, List<Option> options, Number minimum, Number maximum,
                        boolean readOnly, boolean integer) {
        public Field {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(label, "label");
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Option(String label, String value) {
        public Option {
            Objects.requireNonNull(label);
            Objects.requireNonNull(value);
        }
    }

    public enum Type {TEXT, SELECT, NUMBER}
}
