package io.github.chrisshi.mom.auth.application;

import io.github.chrisshi.mom.auth.infrastructure.entity.PermissionResourceEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionMapper;
import io.github.chrisshi.mom.auth.infrastructure.mapper.PermissionResourceMapper;
import io.github.chrisshi.mom.data.page.PageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resource 用例的快速规则测试。
 *
 * <p>Mapper 使用模拟对象，只验证输入规范化、唯一冲突、引用保护和乐观版本；
 * 真实 SQL、事务与迁移由 PostgreSQL IT 覆盖。</p>
 */
class PermissionResourceApplicationTest {
    private PermissionResourceMapper resources;
    private PermissionMapper permissions;
    private PermissionResourceApplication application;

    @BeforeEach
    void setUp() {
        resources = mock(PermissionResourceMapper.class);
        permissions = mock(PermissionMapper.class);
        application = new PermissionResourceApplication(resources, permissions, new PageAdapter(200));
    }

    @Test
    void createMustNormalizeCodesAndProtectUniquePair() {
        when(resources.insert(any(PermissionResourceEntity.class))).thenAnswer(invocation -> {
            PermissionResourceEntity entity = invocation.getArgument(0);
            entity.setId("resource-1");
            return 1;
        });
        var created = application.create(" auth ", " user ", " 用户管理 ", null, 10, true);
        assertThat(created.domainCode()).isEqualTo("AUTH");
        assertThat(created.resourceCode()).isEqualTo("USER");
        assertThat(created.name()).isEqualTo("用户管理");

        when(resources.selectCount(any())).thenReturn(1L);
        assertError(() -> application.create("AUTH", "USER", "重复", null, 10, true),
            AuthErrorCode.RESOURCE_CODE_CONFLICT);
        assertError(() -> application.create("AUTH", "USER:BAD", "非法", null, 10, true),
            AuthErrorCode.RESOURCE_INVALID_CODE);
    }

    @Test
    void concurrentUniqueConflictAndReferencedDeleteMustFailClosed() {
        doThrow(new DuplicateKeyException("uk_auth_permission_resource_domain_code"))
            .when(resources).insert(any(PermissionResourceEntity.class));
        assertError(() -> application.create("AUTH", "USER", "用户管理", null, 10, true),
            AuthErrorCode.RESOURCE_CODE_CONFLICT);

        when(resources.selectOne(any())).thenReturn(resource(true, 2L));
        when(permissions.selectCount(any())).thenReturn(1L);
        assertError(() -> application.delete("resource-1"), AuthErrorCode.RESOURCE_REFERENCED);
        verify(resources, never()).deleteById("resource-1");
    }

    @Test
    void statusAndUpdateMustProtectVersionWithoutCascadingPermissions() {
        PermissionResourceEntity entity = resource(true, 2L);
        when(resources.selectOne(any())).thenReturn(entity);
        assertError(() -> application.disable("resource-1", 1L), AuthErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        when(resources.updateById(entity)).thenReturn(1);
        assertThat(application.disable("resource-1", 2L).enabled()).isFalse();
        verify(permissions, never()).updateById(any(io.github.chrisshi.mom.auth.infrastructure.entity.PermissionEntity.class));
    }

    private static PermissionResourceEntity resource(boolean enabled, long version) {
        PermissionResourceEntity entity = new PermissionResourceEntity();
        entity.setId("resource-1");
        entity.setDomainCode("AUTH");
        entity.setResourceCode("USER");
        entity.setName("用户管理");
        entity.setSortOrder(10);
        entity.setEnabled(enabled);
        entity.setVersion(version);
        return entity;
    }

    private static void assertError(Runnable action, AuthErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AuthException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
