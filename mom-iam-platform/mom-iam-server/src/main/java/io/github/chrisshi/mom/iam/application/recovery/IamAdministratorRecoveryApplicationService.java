package io.github.chrisshi.mom.iam.application.recovery;

import io.github.chrisshi.mom.iam.application.admin.IamSessionRevocationService;
import io.github.chrisshi.mom.iam.application.admin.model.IamSecurityAuditEvent;
import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;
import io.github.chrisshi.mom.iam.application.admin.port.IamPasswordHasher;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditResult;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 一次性内置管理员凭据恢复用例。
 *
 * <p>该 Application Service 只接受启动配置传入的临时明文，使用既有 PasswordHasher 生成摘要，并在
 * 同一 IAM 本地事务中完成版本化凭据替换、Session 撤销和追加型安全审计。它不提供 HTTP 入口，不改变
 * 角色、权限或账号类型，也不把配置、密码、摘要写入日志、异常或审计。Redis Session 撤销或数据库审计
 * 不可用时异常向外传播，使启动 Fail Closed；部分已撤销 Session 无需补偿。</p>
 */
public class IamAdministratorRecoveryApplicationService {
    public static final String BUILT_IN_USERNAME = "admin";
    static final String SYSTEM_ACTOR = "IAM_ADMIN_RECOVERY";
    private static final String EVENT_TYPE = "iam.admin.credential-recovered";

    private final IamAdministratorRecoveryPort administrators;
    private final IamPasswordHasher passwordHasher;
    private final IamSessionRevocationService revocations;
    private final IamSecurityAuditSink audits;
    private final IamIdentifierGenerator ids;
    private final Clock clock;

    /**
     * 创建恢复用例。
     *
     * @param administrators 内置管理员恢复持久化 Port
     * @param passwordHasher IAM 当前密码摘要 Port
     * @param revocations Session 查询与撤销服务
     * @param audits 追加型安全审计 Port
     * @param ids 安全审计 ID 生成器
     * @param clock UTC 时钟
     */
    public IamAdministratorRecoveryApplicationService(
            IamAdministratorRecoveryPort administrators,
            IamPasswordHasher passwordHasher,
            IamSessionRevocationService revocations,
            IamSecurityAuditSink audits,
            IamIdentifierGenerator ids,
            Clock clock) {
        this.administrators = administrators;
        this.passwordHasher = passwordHasher;
        this.revocations = revocations;
        this.audits = audits;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * 恢复固定内置管理员并撤销其全部活动 Session。
     *
     * @param temporaryCredential 12～200 位一次性临时凭据；调用结束后调用方应清空配置对象引用
     * @return 被撤销的 Session 数量
     * @throws IllegalArgumentException 临时凭据不符合长度约束时抛出
     * @throws IllegalStateException 账号不存在、不是有效系统平台管理员、并发冲突或依赖失败时抛出
     * @implNote 该操作不是可重复业务命令。运维方必须在一次成功启动后关闭恢复开关；重复启用会再次
     * 重置凭据并产生新的安全审计。
     * @apiNote 副作用包括凭据摘要更新、失败次数/锁定状态清理、强制改密、Session 撤销和安全审计追加。
     */
    @Transactional
    public RecoveryResult recover(String temporaryCredential) {
        requireCredential(temporaryCredential);
        Instant now = clock.instant();
        IamAdministratorRecoveryPort.AdministratorIdentity administrator = administrators
                .lockByUsername(BUILT_IN_USERNAME)
                .orElseThrow(() -> new IllegalStateException(
                        "IAM built-in administrator does not exist; use bootstrap instead"));
        requireRecoverable(administrator, now);

        String credentialHash = passwordHasher.hash(temporaryCredential);
        administrators.resetCredential(
                administrator.id(), credentialHash, administrator.version(), SYSTEM_ACTOR, now);
        int revokedSessions = revocations.revokeUserSessions(
                administrator.id(), SYSTEM_ACTOR, "administrator_credential_recovery");
        audits.append(successAudit(administrator.id(), revokedSessions, now));
        return new RecoveryResult(revokedSessions);
    }

    /** 校验账号必须是当前有效的内置 INTERNAL 平台管理员。 */
    private void requireRecoverable(
            IamAdministratorRecoveryPort.AdministratorIdentity administrator,
            Instant now) {
        if (!BUILT_IN_USERNAME.equals(administrator.username())
                || !administrator.systemAccount()
                || administrator.userType() != UserType.INTERNAL
                || administrator.status() != IamRecordStatus.ENABLED
                || administrator.deleted()) {
            throw new IllegalStateException(
                    "IAM administrator recovery target is not an enabled built-in INTERNAL account");
        }
        if (!administrators.hasEffectivePlatformAdministratorRole(administrator.id(), now)) {
            throw new IllegalStateException(
                    "IAM administrator recovery target has no effective PLATFORM_ADMIN role");
        }
    }

    /** 使用与第一方登录一致的长度边界做纵深校验，异常不得回显输入值。 */
    private static void requireCredential(String temporaryCredential) {
        if (temporaryCredential == null
                || temporaryCredential.length() < 12
                || temporaryCredential.length() > 200) {
            throw new IllegalArgumentException(
                    "IAM recovery credential must contain 12 to 200 characters");
        }
    }

    /** 构造不含凭据材料、操作人明确为 SYSTEM 的成功审计。 */
    private IamSecurityAuditEvent successAudit(
            String administratorId, int revokedSessions, Instant now) {
        return new IamSecurityAuditEvent(
                ids.nextId(),
                EVENT_TYPE,
                SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH,
                SecurityAuditResult.SUCCESS,
                SecurityAuditActorType.SYSTEM,
                null,
                null,
                "USER",
                administratorId,
                null,
                null,
                null,
                "operator_requested_recovery",
                "内置管理员凭据经显式启动恢复",
                "{\"credentialChangeRequired\":true,\"revokedSessions\":"
                        + revokedSessions + "}",
                null,
                now);
    }

    /** 恢复结果只暴露非敏感的 Session 撤销计数。 */
    public record RecoveryResult(int revokedSessions) {
    }
}
