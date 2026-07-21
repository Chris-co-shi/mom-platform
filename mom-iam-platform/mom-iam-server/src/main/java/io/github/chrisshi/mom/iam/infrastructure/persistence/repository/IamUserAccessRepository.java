package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserApplicationEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserFactoryScopeEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserApplicationMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserFactoryScopeMapper;

import java.util.List;

/** Mobile Access 与 Factory Scope 仓储；Factory 只保存 MDM 引用 ID。 */
public class IamUserAccessRepository {
    private final IamUserApplicationMapper userApplicationMapper;
    private final IamUserFactoryScopeMapper userFactoryScopeMapper;

    /** 创建用户访问范围仓储。 */
    public IamUserAccessRepository(IamUserApplicationMapper userApplicationMapper,
            IamUserFactoryScopeMapper userFactoryScopeMapper) {
        this.userApplicationMapper = userApplicationMapper;
        this.userFactoryScopeMapper = userFactoryScopeMapper;
    }

    /** @param access 已验证的 INTERNAL Mobile Access */
    public void grantApplicationAccess(IamUserApplicationEntity access) {
        requireOne(userApplicationMapper.insert(access), "用户应用访问写入失败");
    }

    /** @param scope Factory Scope 引用 */
    public void grantFactoryScope(IamUserFactoryScopeEntity scope) {
        requireOne(userFactoryScopeMapper.insert(scope), "用户 Factory Scope 写入失败");
    }

    /** @param userId 用户 ID @return 该用户的 Factory Scope */
    public List<IamUserFactoryScopeEntity> listFactoryScopes(String userId) {
        return userFactoryScopeMapper.selectList(Wrappers.<IamUserFactoryScopeEntity>lambdaQuery()
                .eq(IamUserFactoryScopeEntity::getUserId, userId));
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }
}
