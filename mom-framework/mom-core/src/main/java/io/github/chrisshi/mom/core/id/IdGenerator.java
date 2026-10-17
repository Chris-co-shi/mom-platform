package io.github.chrisshi.mom.core.id;

/**
 * 为需要 MOM 技术主键的基础设施提供稳定字符串 ID。
 */
@FunctionalInterface
public interface IdGenerator {
    /**
     * @return 新生成的十进制字符串 ID
     */
    String nextId();
}
