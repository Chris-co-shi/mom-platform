package io.github.chrisshi.mom.coderule;

import java.util.List;
import java.util.Map;

/**
 * 与前端一致的编码规则定义 V1。
 */
public record CodeRuleDefinition(int schemaVersion, List<Segment> segments) {
    public record Segment(String id, String type, Map<String, Object> params) {
    }
}
