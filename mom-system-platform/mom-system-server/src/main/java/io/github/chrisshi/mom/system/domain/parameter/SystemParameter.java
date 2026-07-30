package io.github.chrisshi.mom.system.domain.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.api.ParameterValueType;

import java.time.Instant;

/**
 * System Platform 类型化非敏感参数聚合。
 *
 * <p>聚合只表达 System 自有参数事实，不承载 Secret、Credential、Permission 或 IAM 配置。写入由
 * Application 本地事务编排，数据库唯一约束与版本 CAS 提供并发兜底；实例不可变，可安全跨线程读取。
 * PostgreSQL 不可用时写入和解析直接失败，不返回伪造默认值。</p>
 */
public record SystemParameter(
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

    /** 建立尚未持久化的新参数；ID 与审计由统一数据基础设施填充。 */
    public static SystemParameter newParameter(
            ParameterScopeType scopeType,
            String scopeCode,
            String parameterKey,
            ParameterValueType valueType,
            String parameterValue,
            boolean enabled,
            String description) {
        return new SystemParameter(null, scopeType, scopeCode, parameterKey, valueType, parameterValue,
                enabled, 0L, description, null, null, null, null);
    }

    /** 按客户端版本建立内容更新快照，Scope 与 Key 保持不可变。 */
    public SystemParameter update(
            long expectedVersion,
            ParameterValueType newValueType,
            String newValue,
            String newDescription) {
        return new SystemParameter(id, scopeType, scopeCode, parameterKey, newValueType, newValue,
                enabled, expectedVersion, newDescription, createdBy, createdAt, updatedBy, updatedAt);
    }

    /** 按客户端版本建立启停快照，不允许通过禁用绕过类型一致性。 */
    public SystemParameter changeStatus(long expectedVersion, boolean newEnabled) {
        return new SystemParameter(id, scopeType, scopeCode, parameterKey, valueType, parameterValue,
                newEnabled, expectedVersion, description, createdBy, createdAt, updatedBy, updatedAt);
    }
}
