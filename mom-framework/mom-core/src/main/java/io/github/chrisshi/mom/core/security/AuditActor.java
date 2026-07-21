package io.github.chrisshi.mom.core.security;

import java.util.Objects;

/**
 * 一次数据写入或安全操作的不可变操作人快照。
 *
 * <p>该模型只保留审计链路需要的稳定身份信息，不包含角色、权限、Factory Scope 或 Party Scope，不能替代
 * 完整 CurrentUser。可选字段缺失时保持 {@code null}，不得使用无边界 Map 或伪造默认值。</p>
 *
 * @param actorId 用户 ID 或稳定 SYSTEM Actor Code，必填
 * @param actorType 操作人类型，必填
 * @param userType P1.5 用户类型；SYSTEM 或尚未具备该 Claim 时可为空
 * @param clientId OAuth Client ID；非请求场景可为空
 * @param sessionId JWT {@code sid}；非用户 Session 场景可为空
 * @param correlationId 关联标识；无同步请求上下文时可为空
 */
public record AuditActor(
        String actorId,
        ActorType actorType,
        String userType,
        String clientId,
        String sessionId,
        String correlationId) {

    /**
     * 校验必填身份并规范化可选文本。
     */
    public AuditActor {
        actorId = requireText(actorId, "actorId");
        actorType = Objects.requireNonNull(actorType, "actorType 不能为空");
        userType = normalizeOptional(userType);
        clientId = normalizeOptional(clientId);
        sessionId = normalizeOptional(sessionId);
        correlationId = normalizeOptional(correlationId);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
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
