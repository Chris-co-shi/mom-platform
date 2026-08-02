package io.github.chrisshi.mom.system.application.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameter;

import java.time.Instant;
import java.util.List;

/**
 * System Parameter 用例的 Command、Query 与只读 View。
 *
 * <p>这些类型位于 Application 边界，不携带 HTTP、MyBatis Entity 或客户端审计字段。写命令无法提交
 * createdBy/updatedBy，审计主体只能由现有认证上下文和 mom-data 填充。</p>
 */
public final class SystemParameterApplicationModels {
    private SystemParameterApplicationModels() {
    }

    /** 创建参数命令。 */
    public record CreateCommand(
            ParameterScopeType scopeType,
            String scopeCode,
            String parameterKey,
            ParameterValueType valueType,
            String parameterValue,
            String description,
            Boolean enabled) {
    }

    /** 更新值命令；Scope 与 Key 不允许变更。 */
    public record UpdateCommand(
            long version,
            ParameterValueType valueType,
            String parameterValue,
            String description) {
    }

    /** 版本化启停命令。 */
    public record StatusCommand(boolean enabled, long version) {
    }

    /** 管理分页查询；所有字符串条件均为精确匹配。 */
    public record PageQuery(
            ParameterScopeType scopeType,
            String scopeCode,
            String parameterKey,
            Boolean enabled,
            int page,
            int size) {
    }

    /** 管理参数只读视图。 */
    public record ParameterView(
            String id,
            ParameterScopeType scopeType,
            String scopeCode,
            String parameterKey,
            ParameterValueType valueType,
            String parameterValue,
            boolean enabled,
            long version,
            String description,
            String createdBy,
            Instant createdAt,
            String updatedBy,
            Instant updatedAt) {
        /** 从领域聚合映射为对外管理视图。 */
        public static ParameterView from(SystemParameter parameter) {
            return new ParameterView(parameter.id(), parameter.scopeType(), parameter.scopeCode(),
                    parameter.parameterKey(), parameter.valueType(), parameter.parameterValue(),
                    parameter.enabled(), parameter.version(), parameter.description(), parameter.createdBy(),
                    parameter.createdAt(), parameter.updatedBy(), parameter.updatedAt());
        }
    }

    /** 固定排序的分页结果。 */
    public record PageView(List<ParameterView> items, long total, int page, int size) {
        public PageView {
            items = List.copyOf(items);
        }
    }
}
