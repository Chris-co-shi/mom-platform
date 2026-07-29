package io.github.chrisshi.mom.iam.admin;

/**
 * IAM Admin 已发布兼容错误响应。
 *
 * <p>该 record 只表达稳定机器码和可观察说明，序列化后仍严格只有 {@code code}
 * 与 {@code message}。它不与第一方认证 JSON、OAuth2/OIDC、Spring Security 或 Gateway
 * 错误模型合并，不容纳 SQL、Token、堆栈或底层约束文本。</p>
 *
 * @param code 稳定机器错误码
 * @param message 已脱敏的兼容说明
 */
public record IamAdminErrorResponse(String code, String message) {
}
