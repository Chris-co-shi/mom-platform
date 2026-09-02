package io.github.chrisshi.mom.core.security;

import java.util.Objects;

/**
 * 一次数据写入或安全操作的不可变操作人快照。
 *
 * <p>该模型只保留审计链路需要的稳定身份信息，不包含角色、权限、Factory Scope 或 Party Scope，不能替代
 * 完整 CurrentUser。可选字段缺失时保持 {@code null}，不得使用无边界 Map 或伪造默认值。</p>
 *
 * @param actorId   用户 ID 或稳定 SYSTEM Actor Code，必填
 * @param actorType 操作人类型，必填
 */
public record AuditActor(
    String actorId,
    ActorType actorType) {

    /**
     * 校验必填身份并规范化可选文本。
     */
    public AuditActor {
        actorId = requireText(actorId);
        actorType = Objects.requireNonNull(actorType, "actorType 不能为空");
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("actorId" + " 不能为空");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
