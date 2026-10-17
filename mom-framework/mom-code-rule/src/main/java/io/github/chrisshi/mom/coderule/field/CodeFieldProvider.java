package io.github.chrisshi.mom.coderule.field;

/** 由 Owner 解析真实业务字段；Framework 不持有业务实体。 */
public interface CodeFieldProvider {
    CodeFieldMetadata metadata();

    /** 仅从可信业务上下文解析字段值。 */
    String resolve(CodeGenerationContext context);
}
