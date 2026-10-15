package io.github.chrisshi.mom.qms.application;

import java.math.BigDecimal;
import java.util.Locale;

/** 质量定义共享的输入规范化和精确数值规则。 */
final class QmsRules {
    private QmsRules() { }
    static String id(String value) {
        if (value == null || !value.matches("[0-9]{1,19}")) throw QmsException.invalidReference();
        return value;
    }
    static String code(String value) {
        if (value == null || value.isBlank()) throw QmsException.invalidReference();
        return value.trim().toUpperCase(Locale.ROOT);
    }
    static String name(String value) {
        if (value == null || value.isBlank()) throw QmsException.invalidReference();
        return value.trim();
    }
    static BigDecimal optionalDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException exception) { throw QmsException.invalidRange(); }
    }
    static void requireVersion(Long actual, Long expected) {
        if (expected == null || !actual.equals(expected)) throw QmsException.versionConflict();
    }
}
