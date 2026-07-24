package io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamExternalUserBindingEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserApplicationEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserFactoryScopeEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamExternalUserBindingMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserApplicationMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserFactoryScopeMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Factory Scope、Mobile Access 与 Party Binding 管理仓储。 */
public final class IamUserAccessAdminRepository {
    private final IamUserFactoryScopeMapper factoryScopeMapper;
    private final IamUserApplicationMapper applicationMapper;
    private final IamExternalUserBindingMapper bindingMapper;

    /** 创建用户访问范围管理仓储。 */
    public IamUserAccessAdminRepository(
            IamUserFactoryScopeMapper factoryScopeMapper,
            IamUserApplicationMapper applicationMapper,
            IamExternalUserBindingMapper bindingMapper) {
        this.factoryScopeMapper = factoryScopeMapper;
        this.applicationMapper = applicationMapper;
        this.bindingMapper = bindingMapper;
    }

    /** @return 用户当前启用的 Factory ID */
    public Set<String> factoryIds(String userId) {
        return Set.copyOf(new LinkedHashSet<>(factoryScopeMapper.selectFactoryIds(userId)));
    }

    /** 全量替换 Factory Scope；父用户行锁与版本推进由同一应用事务持有。 */
    public void replaceFactoryScopes(
            String userId, Collection<String> factoryIds, String actor, Instant now,
            Supplier<String> idSupplier) {
        factoryScopeMapper.deleteByUserId(userId);
        for (String factoryId : factoryIds) {
            IamUserFactoryScopeEntity scope = new IamUserFactoryScopeEntity();
            scope.setId(idSupplier.get());
            scope.setUserId(userId);
            scope.setFactoryId(factoryId);
            scope.setStatus(IamRecordStatus.ENABLED);
            scope.setCreatedAt(now);
            scope.setCreatedBy(actor);
            scope.setUpdatedAt(now);
            scope.setUpdatedBy(actor);
            scope.setVersion(0L);
            requireOne(factoryScopeMapper.insert(scope), "用户 Factory Scope 写入失败");
        }
    }

    /** @return 用户当前是否拥有有效 Mobile Access */
    public boolean mobileAccessEnabled(String userId) {
        return applicationMapper.countEffective(userId, ApplicationCode.MOM_MOBILE_PDA) > 0;
    }

    /** 设置 Mobile Access；已有记录原子更新，不存在时插入受审计的新记录。 */
    public void setMobileAccess(
            String userId, boolean enabled, String actor, Instant now, Supplier<String> idSupplier) {
        IamRecordStatus status = enabled ? IamRecordStatus.ENABLED : IamRecordStatus.DISABLED;
        if (applicationMapper.updateAccess(
                userId, ApplicationCode.MOM_MOBILE_PDA, status, now, actor) > 0) {
            return;
        }
        IamUserApplicationEntity access = new IamUserApplicationEntity();
        access.setId(idSupplier.get());
        access.setUserId(userId);
        access.setApplicationCode(ApplicationCode.MOM_MOBILE_PDA);
        access.setStatus(status);
        access.setCreatedAt(now);
        access.setCreatedBy(actor);
        access.setUpdatedAt(now);
        access.setUpdatedBy(actor);
        access.setVersion(0L);
        requireOne(applicationMapper.insert(access), "用户 Mobile Access 写入失败");
    }

    /** @return 用户当前 Party Binding 管理投影 */
    public Optional<IamAdminViews.PartyBindingView> partyBinding(String userId) {
        return Optional.ofNullable(bindingMapper.selectByUserId(userId)).map(binding ->
                new IamAdminViews.PartyBindingView(
                        binding.getId(), binding.getPartyType(), binding.getPartyId(),
                        binding.getStatus(), binding.getVersion()));
    }

    /** 重绑外部 Party；已有记录更新，不存在时插入。 */
    public void rebindParty(
            String userId, PartyType partyType, String partyId, String actor,
            Instant now, Supplier<String> idSupplier) {
        if (bindingMapper.rebind(userId, partyType, partyId, now, actor) > 0) {
            return;
        }
        IamExternalUserBindingEntity binding = new IamExternalUserBindingEntity();
        binding.setId(idSupplier.get());
        binding.setUserId(userId);
        binding.setPartyType(partyType);
        binding.setPartyId(partyId);
        binding.setStatus(IamRecordStatus.ENABLED);
        binding.setValidFrom(now);
        binding.setCreatedAt(now);
        binding.setCreatedBy(actor);
        binding.setUpdatedAt(now);
        binding.setUpdatedBy(actor);
        binding.setVersion(0L);
        requireOne(bindingMapper.insert(binding), "外部 Party Binding 写入失败");
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
