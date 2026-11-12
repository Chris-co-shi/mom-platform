package io.github.chrisshi.mom.wms.infrastructure.mapper;

import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;

/** MySQL 唯一键冲突只在约束名与当前写入动作匹配时作为业务重放或竞争处理。 */
final class WmsMysqlDuplicateKey {
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;

    private WmsMysqlDuplicateKey() {
    }

    static boolean matches(DuplicateKeyException failure, String table, String key) {
        if (!(failure.getMostSpecificCause() instanceof SQLException sql)
                || sql.getErrorCode() != MYSQL_DUPLICATE_ENTRY) {
            return false;
        }
        String message = sql.getMessage();
        return message.endsWith(" '" + key + "'") || message.endsWith(" '" + table + "." + key + "'");
    }
}
