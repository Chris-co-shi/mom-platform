package io.github.chrisshi.mom.coderule;

import java.time.Instant;

/**
 * 当前 Owner 单个规则修订的读取视图；使用场景和说明由整个规则族共享，历史未补充数据允许为空。
 */
public record CodeRuleView(String id, String ruleId, String ruleCode, String usageScenario, String description, int revisionNo,
                           String status, CodeRuleDefinition definition, long ruleVersion,
                           Instant createdAt, Instant updatedAt) {
}
