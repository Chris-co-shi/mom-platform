package io.github.chrisshi.mom.system.api;

import java.time.Instant;

/**
 * 应用读取到的 System Parameter 有效值。
 *
 * <p>该只读 DTO 保留规范字符串及明确类型，避免跨服务任意对象反序列化。版本和更新时间用于调用方观察
 * 变化，不构成缓存一致性协议；解析失败或服务不可用时调用方不得把错误静默伪装成参数不存在。</p>
 *
 * @param parameterKey 规范化参数键
 * @param valueType 值类型
 * @param parameterValue 规范字符串值
 * @param resolvedScopeType 实际命中的作用域
 * @param resolvedScopeCode 实际命中的作用域编码；GLOBAL 为规范空字符串
 * @param version 参数乐观锁版本
 * @param updatedAt 最近更新时间
 */
public record ResolvedSystemParameter(
        String parameterKey,
        ParameterValueType valueType,
        String parameterValue,
        ParameterScopeType resolvedScopeType,
        String resolvedScopeCode,
        long version,
        Instant updatedAt) {
}
