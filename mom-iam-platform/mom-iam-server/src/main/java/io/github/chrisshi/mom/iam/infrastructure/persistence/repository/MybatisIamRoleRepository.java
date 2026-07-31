package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamRoleAdminQueryPort;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.role.IamRoleRepository;
import io.github.chrisshi.mom.iam.domain.exception.IamStaleVersionException;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRoleEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRoleMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** MyBatis-Plus IAM Role 聚合 Adapter。 */
public class MybatisIamRoleRepository
        extends CrudRepository<IamRoleMapper, IamRoleEntity>
        implements IamRoleRepository, IamRoleAdminQueryPort {

    @Override
    public Optional<IamRole> lockById(String roleId) {
        return Optional.ofNullable(getBaseMapper().selectAdminForUpdate(roleId))
                .map(MybatisIamRoleRepository::toDomain);
    }

    @Override
    public List<IamRole> findByIds(Collection<String> roleIds) {
        return roleIds == null || roleIds.isEmpty()
                ? List.of()
                : listByIds(roleIds).stream().map(MybatisIamRoleRepository::toDomain).toList();
    }

    @Override
    public void create(IamRole role, String actor, Instant now) {
        IamRoleEntity entity = new IamRoleEntity();
        entity.setId(role.id());
        entity.setCode(role.code());
        entity.setName(role.name());
        entity.setApplicableUserType(role.applicableUserType());
        entity.setStatus(role.status());
        entity.setBuiltIn(role.builtIn());
        entity.setDescription(role.description());
        entity.setCreatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor);
        entity.setVersion(role.version());
        entity.setDeleted(Boolean.FALSE);
        if (!save(entity)) throw new IllegalStateException("角色创建失败");
    }

    @Override
    public void update(
            String roleId, String name, String description, IamRecordStatus status,
            long expectedVersion, String actor, Instant now) {
        if (getBaseMapper().updateAdminRole(
                roleId, name, description, status, expectedVersion, actor, now) != 1) {
            throw new IamStaleVersionException("角色已被并发修改");
        }
    }

    @Override
    public List<IamAdminViews.RoleView> listRoles(
            String userType, int limit, int offset) {
        return getBaseMapper().selectAdminRoles(userType, limit, offset);
    }

    @Override
    public Optional<IamAdminViews.RoleView> findRole(String roleId) {
        IamRoleEntity entity = getById(roleId);
        return Optional.ofNullable(entity).map(item -> new IamAdminViews.RoleView(
                item.getId(), item.getCode(), item.getName(), item.getApplicableUserType(),
                item.getStatus(), Boolean.TRUE.equals(item.getBuiltIn()),
                item.getDescription(), item.getVersion()));
    }

    public static IamRole toDomain(IamRoleEntity entity) {
        return new IamRole(
                entity.getId(), entity.getCode(), entity.getName(),
                entity.getApplicableUserType(), entity.getStatus(),
                Boolean.TRUE.equals(entity.getBuiltIn()), entity.getDescription(),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    public static IamRole toDomain(IamAdminViews.RoleView view) {
        return new IamRole(
                view.id(), view.code(), view.name(), view.applicableUserType(),
                view.status(), view.builtIn(), view.description(), view.version());
    }
}
