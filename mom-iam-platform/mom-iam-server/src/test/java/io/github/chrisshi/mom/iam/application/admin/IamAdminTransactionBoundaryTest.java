package io.github.chrisshi.mom.iam.application.admin;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** IAM Admin 16 个公开写用例事务边界门禁。 */
class IamAdminTransactionBoundaryTest {

    @Test
    void applicationServicesMustOwnExactlyThePublishedWriteTransactions() {
        assertTransactional(IamUserAdminApplicationService.class,
                "createUser", "updateUser", "setUserStatus", "unlockUser",
                "resetPassword", "deleteUser");
        assertTransactional(IamUserAuthorizationApplicationService.class,
                "replaceUserRoles", "replaceFactoryScopes", "setMobileAccess", "rebindParty");
        assertTransactional(IamRoleAdminApplicationService.class,
                "createRole", "updateRole", "replaceRolePermissions");
        assertTransactional(IamSessionAdminApplicationService.class,
                "revokeSession", "revokeAllSessions");
        assertTransactional(IamClientAdminApplicationService.class, "setClientStatus");
    }

    private static void assertTransactional(Class<?> type, String... expectedMethods) {
        assertThat(Modifier.isPublic(type.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(type.getModifiers())).isFalse();
        assertThat(transactionalMethods(type)).containsExactlyInAnyOrder(expectedMethods);
        Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .forEach(method -> assertThat(Modifier.isPublic(method.getModifiers())).isTrue());
    }

    private static Set<String> transactionalMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
