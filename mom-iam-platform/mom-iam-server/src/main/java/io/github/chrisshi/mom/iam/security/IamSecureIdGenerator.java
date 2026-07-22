package io.github.chrisshi.mom.iam.security;

import java.security.SecureRandom;

/** 为 Session 与 Refresh 状态生成正数 19 位以内随机 String ID。 */
public final class IamSecureIdGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String nextId() {
        long value;
        do {
            value = secureRandom.nextLong(Long.MAX_VALUE);
        }
        while (value == 0L);
        return Long.toString(value);
    }
}
