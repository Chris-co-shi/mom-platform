package io.github.chrisshi.mom.iam.domain.role;

import io.github.chrisshi.mom.iam.domain.exception.IamDomainConflictException;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Role 聚合可变性与分配资格测试。 */
class IamRoleTest {

    @Test
    void builtInRoleMustBeImmutable() {
        IamRole role = new IamRole(
                "400", "PLATFORM_ADMIN", "Admin", UserType.INTERNAL,
                IamRecordStatus.ENABLED, true, null, 0L);
        assertThatThrownBy(() -> role.change(
                "Changed", null, IamRecordStatus.ENABLED, 0L))
                .isInstanceOf(IamDomainConflictException.class)
                .hasMessage("内置角色在 P1.5 管理 API 中只读");
    }

    @Test
    void disabledRoleMustNotBeAssigned() {
        IamRole role = new IamRole(
                "401", "OPERATOR", "Operator", UserType.INTERNAL,
                IamRecordStatus.DISABLED, false, null, 0L);
        assertThatThrownBy(() -> role.requireAssignableTo(UserType.INTERNAL))
                .isInstanceOf(IamDomainConflictException.class)
                .hasMessage("禁用角色不能分配");
    }
}
