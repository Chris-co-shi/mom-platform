package io.github.chrisshi.mom.iam.domain.user;

import io.github.chrisshi.mom.iam.domain.exception.IamDomainConflictException;
import io.github.chrisshi.mom.iam.domain.exception.IamStaleVersionException;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** User 聚合状态与并发不变量测试。 */
class IamUserAccountTest {

    @Test
    void userMustRejectSelfDisableAndStaleVersion() {
        IamUserAccount user = user(3L);
        assertThatThrownBy(() ->
                user.changeStatus("100", IamRecordStatus.DISABLED, 3L))
                .isInstanceOf(IamDomainConflictException.class)
                .hasMessage("不能禁用当前登录账号");
        assertThatThrownBy(() ->
                user.changeStatus("200", IamRecordStatus.DISABLED, 2L))
                .isInstanceOf(IamStaleVersionException.class);
    }

    private static IamUserAccount user(long version) {
        return new IamUserAccount(
                "100", "user", "User", UserType.INTERNAL,
                IamRecordStatus.ENABLED, 0, null,
                false, false, null, version);
    }
}
