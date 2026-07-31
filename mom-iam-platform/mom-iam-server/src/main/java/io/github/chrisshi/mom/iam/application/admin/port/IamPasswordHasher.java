package io.github.chrisshi.mom.iam.application.admin.port;

/** 密码摘要生成 Port；Application 不依赖 Spring PasswordEncoder。 */
public interface IamPasswordHasher {
    String hash(String rawPassword);
}
