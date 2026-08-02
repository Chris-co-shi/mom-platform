package io.github.chrisshi.mom.system.api;

/**
 * System Parameter 的稳定值类型契约。
 *
 * <p>跨服务始终传输该类型和规范字符串，不反序列化成任意 Java Object。该枚举明确排除 Secret、脚本、
 * 表达式和二进制等高风险类型；新增类型必须另行定义规范化、兼容和安全语义。</p>
 */
public enum ParameterValueType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    JSON
}
