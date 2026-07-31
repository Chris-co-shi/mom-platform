package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.application.admin.port.IamUserAccessPort;
import io.github.chrisshi.mom.iam.domain.authorization.IamPartyBinding;
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
import java.util.Optional;
import java.util.function.Supplier;

/** Factory、Mobile 与 Party 三表访问 Adapter。 */
public final class MybatisIamUserAccessRepository implements IamUserAccessPort {
    private final IamUserFactoryScopeMapper factoryScopeMapper;
    private final IamUserApplicationMapper applicationMapper;
    private final IamExternalUserBindingMapper bindingMapper;

    public MybatisIamUserAccessRepository(
            IamUserFactoryScopeMapper factoryScopeMapper,
            IamUserApplicationMapper applicationMapper,
            IamExternalUserBindingMapper bindingMapper) {
        this.factoryScopeMapper = factoryScopeMapper;
        this.applicationMapper = applicationMapper;
        this.bindingMapper = bindingMapper;
    }

    @Override
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

    @Override
    public void setMobileAccess(
            String userId, boolean enabled, String actor, Instant now,
            Supplier<String> idSupplier) {
        IamRecordStatus status =
                enabled ? IamRecordStatus.ENABLED : IamRecordStatus.DISABLED;
        if (applicationMapper.updateAccess(
                userId, ApplicationCode.MOM_MOBILE_PDA, status, now, actor) > 0) return;
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

    @Override
    public Optional<IamPartyBinding> partyBinding(String userId) {
        return Optional.ofNullable(bindingMapper.selectOne(
                Wrappers.<IamExternalUserBindingEntity>lambdaQuery()
                        .eq(IamExternalUserBindingEntity::getUserId, userId)))
                .map(entity -> new IamPartyBinding(
                        entity.getId(), entity.getPartyType(), entity.getPartyId(),
                        entity.getStatus(), entity.getVersion()));
    }

    @Override
    public void rebindParty(
            String userId, PartyType partyType, String partyId, String actor,
            Instant now, Supplier<String> idSupplier) {
        IamExternalUserBindingEntity changed = new IamExternalUserBindingEntity();
        changed.setPartyType(partyType);
        changed.setPartyId(partyId);
        changed.setStatus(IamRecordStatus.ENABLED);
        changed.setValidFrom(now);
        changed.setValidUntil(null);
        changed.setUpdatedAt(now);
        changed.setUpdatedBy(actor);
        if (bindingMapper.update(
                changed,
                Wrappers.<IamExternalUserBindingEntity>lambdaUpdate()
                        .eq(IamExternalUserBindingEntity::getUserId, userId)
                        .set(IamExternalUserBindingEntity::getValidUntil, null)
                        .setSql("version = version + 1")) > 0) return;
        IamExternalUserBindingEntity entity = new IamExternalUserBindingEntity();
        entity.setId(idSupplier.get());
        entity.setUserId(userId);
        entity.setPartyType(partyType);
        entity.setPartyId(partyId);
        entity.setStatus(IamRecordStatus.ENABLED);
        entity.setValidFrom(now);
        entity.setCreatedAt(now);
        entity.setCreatedBy(actor);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(actor);
        entity.setVersion(0L);
        requireOne(bindingMapper.insert(entity), "外部 Party Binding 写入失败");
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }
}
