package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;

import java.security.SecureRandom;

/** 为 IAM 状态生成正数 19 位以内随机 String ID。 */
public final class IamSecureIdGenerator implements IamIdentifierGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String nextId() {
        long value;
        do {
            value = secureRandom.nextLong(Long.MAX_VALUE);
        }
        while (value == 0L);
        return Long.toString(value);
    }
}
