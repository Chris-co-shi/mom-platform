package io.github.chrisshi.mom.coderule;

/** 启动配置中的 Owner 标识；限制长度以保证固定后缀表名不超过 PostgreSQL 的 63 字节。 */
public record CodeRuleOwner(String value) {
    /** 仅接受小写 ASCII 数据库标识符，不接受 Schema、表名或 SQL。 */
    public CodeRuleOwner {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,43}")) {
            throw new IllegalArgumentException("mom.code-rule.owner 必须为 1～44 位小写 ASCII 标识符");
        }
    }
}
