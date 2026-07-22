package io.github.chrisshi.mom.iam.security;

/**
 * 保留空类型用于兼容已发布的阶段提交历史。
 *
 * <p>密码 AuthenticationProvider 直接在 IAM Authorization AutoConfiguration 中标记为 Primary，
 * 不再创建第二个代理 Bean。</p>
 */
final class IamPasswordAuthenticationProviderSelection {
    private IamPasswordAuthenticationProviderSelection() {
    }
}
