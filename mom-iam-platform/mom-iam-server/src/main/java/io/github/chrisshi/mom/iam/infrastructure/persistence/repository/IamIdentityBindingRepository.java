package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamExternalUserBindingEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamInternalUserProfileEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamExternalUserBindingMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamInternalUserProfileMapper;

import java.util.Optional;

/** 内部资料与外部 Party Binding 的持久化边界，不跨 Schema 查询业务主数据。 */
public class IamIdentityBindingRepository {
    private final IamInternalUserProfileMapper internalProfileMapper;
    private final IamExternalUserBindingMapper externalBindingMapper;

    /** 创建身份扩展仓储。 */
    public IamIdentityBindingRepository(IamInternalUserProfileMapper internalProfileMapper,
            IamExternalUserBindingMapper externalBindingMapper) {
        this.internalProfileMapper = internalProfileMapper;
        this.externalBindingMapper = externalBindingMapper;
    }

    /** @param profile 已验证为 INTERNAL 用户的资料 */
    public void insertInternalProfile(IamInternalUserProfileEntity profile) {
        requireOne(internalProfileMapper.insert(profile), "内部用户资料写入失败");
    }

    /** @param binding 已完成用户与 Party 类型校验的绑定 */
    public void insertExternalBinding(IamExternalUserBindingEntity binding) {
        requireOne(externalBindingMapper.insert(binding), "外部主体绑定写入失败");
    }

    /** @param userId 用户 ID @return 唯一外部绑定 */
    public Optional<IamExternalUserBindingEntity> findExternalBindingByUserId(String userId) {
        return Optional.ofNullable(externalBindingMapper.selectOne(
                Wrappers.<IamExternalUserBindingEntity>lambdaQuery()
                        .eq(IamExternalUserBindingEntity::getUserId, userId)));
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }
}
