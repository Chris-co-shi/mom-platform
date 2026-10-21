package io.github.chrisshi.mom.coderule.application;

/** 正式编码及实际使用的规则修订；同一请求键重试返回原修订信息。 */
public record CodeGenerationResult(String ruleCode, String revisionId, int revisionNo, String code) { }
