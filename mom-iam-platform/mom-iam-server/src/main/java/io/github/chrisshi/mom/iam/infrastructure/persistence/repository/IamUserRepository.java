package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;

import java.util.Optional;

/** IAM 用户明确用途仓储，不暴露万能 CRUD Service。 */
public class IamUserRepository {
    private final IamUserMapper mapper;

    /** @param mapper 用户 Mapper */
    public IamUserRepository(IamUserMapper mapper) { this.mapper = mapper; }

    /** @param username 全局唯一用户名 @return 未逻辑删除账号 */
    public Optional<IamUserEntity> findByUsername(String username) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<IamUserEntity>lambdaQuery()
                .eq(IamUserEntity::getUsername, username)));
    }

    /** @param user 已完成领域校验的用户 */
    public void insert(IamUserEntity user) {
        if (mapper.insert(user) != 1) throw new IllegalStateException("IAM 用户写入失败");
    }

    /** @param user 携带 ID 与版本的用户 @return 更新是否成功 */
    public boolean updateById(IamUserEntity user) { return mapper.updateById(user) == 1; }
}
