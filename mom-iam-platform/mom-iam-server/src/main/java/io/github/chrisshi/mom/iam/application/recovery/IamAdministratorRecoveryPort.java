package io.github.chrisshi.mom.iam.application.recovery;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;

import java.time.Instant;
import java.util.Optional;

/**
 * 内置管理员恢复用例的持久化端口。
 *
 * <p>该端口只暴露恢复所需的非敏感身份、PLATFORM_ADMIN 有效性和版本化凭据更新，不返回密码摘要，
 * 也不允许调用方访问 Mapper。Infrastructure 必须使用同一 IAM DataSource，并在更新行数异常时
 * Fail Closed；Application 负责事务、身份不变量、Session 撤销和安全审计。</p>
 */
public interface IamAdministratorRecoveryPort {

    /**
     * 锁定固定用户名对应的恢复目标。
     *
     * @param username 仅允许传入内置管理员用户名
     * @return 不含凭据材料的身份；账号不存在时为空
     */
    Optional<AdministratorIdentity> lockByUsername(String username);

    /**
     * 判断目标是否仍拥有当前有效的内置平台管理员角色。
     *
     * @param userId 用户 ID
     * @param now 当前 UTC 时间
     * @return 角色、关系和账号在当前时间均有效时返回 true
     */
    boolean hasEffectivePlatformAdministratorRole(String userId, Instant now);

    /**
     * 使用聚合版本 CAS 替换凭据，并清除失败次数和锁定状态、强制后续改密。
     *
     * @param userId 用户 ID
     * @param credentialHash 新凭据摘要；实现不得记录或返回
     * @param expectedVersion 已锁定身份的版本
     * @param actor 稳定 SYSTEM Actor Code
     * @param now 当前 UTC 时间
     * @throws IllegalStateException 更新行数不是 1 时抛出
     */
    void resetCredential(
            String userId,
            String credentialHash,
            long expectedVersion,
            String actor,
            Instant now);

    /** 恢复用例所需的最小非敏感管理员身份。 */
    record AdministratorIdentity(
            String id,
            String username,
            UserType userType,
            IamRecordStatus status,
            boolean systemAccount,
            long version,
            boolean deleted) {
    }
}
