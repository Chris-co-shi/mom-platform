package io.github.chrisshi.mom.iam.domain.authorization;

import io.github.chrisshi.mom.iam.domain.exception.IamDomainConflictException;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccount;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** User Authorization 聚合关系不变量测试。 */
class IamUserAuthorizationTest {

    @Test
    void externalFactoryScopeMustRequireEnabledPartyBinding() {
        IamUserAccount supplier = new IamUserAccount(
                "200", "supplier", "Supplier", UserType.SUPPLIER,
                IamRecordStatus.ENABLED, 0, null,
                false, false, null, 2L);
        IamUserAuthorization authorization =
                new IamUserAuthorization(supplier, null);

        assertThatThrownBy(() ->
                authorization.replaceFactoryScopes(Set.of("300"), 2L))
                .isInstanceOf(IamDomainConflictException.class)
                .hasMessage("外部用户缺少有效 Party Binding");
    }

    @Test
    void mobileAccessMustRemainInternalOnly() {
        IamUserAccount customer = new IamUserAccount(
                "200", "customer", "Customer", UserType.CUSTOMER,
                IamRecordStatus.ENABLED, 0, null,
                false, false, null, 2L);
        IamUserAuthorization authorization =
                new IamUserAuthorization(customer, null);

        assertThatThrownBy(() -> authorization.setMobileAccess(true, 2L))
                .isInstanceOf(IamDomainConflictException.class)
                .hasMessage("Mobile Access 只允许 INTERNAL 用户");
    }
}
