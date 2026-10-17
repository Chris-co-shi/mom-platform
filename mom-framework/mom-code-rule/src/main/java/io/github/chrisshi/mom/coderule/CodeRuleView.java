package io.github.chrisshi.mom.coderule;

import java.time.Instant;

/**
 * 当前 Owner 单个规则修订的读取视图。
 */
public record CodeRuleView(String id, String ruleId, String ruleCode, int revisionNo,
                           String status, CodeRuleDefinition definition, long ruleVersion,
                           Instant createdAt, Instant updatedAt) {
}
