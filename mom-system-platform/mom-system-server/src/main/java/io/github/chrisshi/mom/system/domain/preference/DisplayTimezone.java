package io.github.chrisshi.mom.system.domain.preference;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * 仅用于客户端显示的 IANA Zone ID 值对象。
 *
 * <p>它不参与 Factory 业务日期或服务端事务边界。校验依赖 JDK 25 TZDB 中的可用 Zone ID；固定 Offset、
 * GMT/UTC 自定义偏移和未知 Zone 均失败，不依赖宿主机默认时区。</p>
 */
public record DisplayTimezone(String value) {
    public DisplayTimezone {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || value.startsWith("GMT+") || value.startsWith("GMT-")
                || value.startsWith("UTC+") || value.startsWith("UTC-")) {
            throw new PreferenceValidationException("invalid_timezone", "displayTimezone 必须是 IANA Zone ID");
        }
        try {
            ZoneId zoneId = ZoneId.of(value);
            if (!"UTC".equals(value) && !ZoneId.getAvailableZoneIds().contains(value)) {
                throw new PreferenceValidationException("invalid_timezone", "displayTimezone 必须是 IANA Zone ID");
            }
            if (!zoneId.getId().equals(value)) {
                throw new PreferenceValidationException("invalid_timezone", "displayTimezone 必须是 IANA Zone ID");
            }
        } catch (DateTimeException exception) {
            throw new PreferenceValidationException(
                    "invalid_timezone", "displayTimezone 必须是 IANA Zone ID", exception);
        }
    }
}
