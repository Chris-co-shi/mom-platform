package io.github.chrisshi.mom.iam.application.admin.model;

/** Web Adapter 传入的非敏感请求审计上下文。 */
public record IamAdminRequestContext(String ipAddress, String userAgent) { }
