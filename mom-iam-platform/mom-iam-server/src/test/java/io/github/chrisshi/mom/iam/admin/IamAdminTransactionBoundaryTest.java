package io.github.chrisshi.mom.iam.admin;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S09 IAM Admin 事务边界结构门禁。
 *
 * <p>测试保证兼容 Facade 不重复开启事务，原 16 个写用例的事务精确迁移到可被
 * Spring 基于类代理的 public Application Service 方法。它不证明 PostgreSQL 行锁本身，
 * 行锁与调用顺序由用例特征测试和 Repository SQL 负责。</p>
 */
class IamAdminTransactionBoundaryTest {

    /** Facade 不得形成嵌套事务或隐藏真正事务所有者。 */
    @Test
    void compatibilityFacadeMustNotOwnTransactions() {
        assertThat(transactionalMethods(IamAdminService.class)).isEmpty();
    }

    /** 全部既有写用例必须继续是可代理的 public 事务方法。 */
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
        assertThat(Modifier.isPublic(type.getModifiers())).as(type.getSimpleName()).isTrue();
        assertThat(Modifier.isFinal(type.getModifiers())).as(type.getSimpleName()).isFalse();
        assertThat(transactionalMethods(type)).containsExactlyInAnyOrder(expectedMethods);
        Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .forEach(method -> assertThat(Modifier.isPublic(method.getModifiers()))
                        .as(type.getSimpleName() + "#" + method.getName()).isTrue());
    }

    private static Set<String> transactionalMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
