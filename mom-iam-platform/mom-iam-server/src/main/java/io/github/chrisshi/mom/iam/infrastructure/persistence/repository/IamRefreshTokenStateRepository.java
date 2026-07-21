package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRefreshTokenEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamRefreshTokenMapper;

import java.util.Optional;

/** Refresh Token 摘要状态仓储；不生成 Token、不计算 HMAC、不执行 Rotation。 */
public class IamRefreshTokenStateRepository {
    private final IamRefreshTokenMapper mapper;

    /** @param mapper Refresh Token Mapper */
    public IamRefreshTokenStateRepository(IamRefreshTokenMapper mapper) { this.mapper = mapper; }

    /** @param tokenState 摘要状态 */
    public void insert(IamRefreshTokenEntity tokenState) {
        if (mapper.insert(tokenState) != 1) throw new IllegalStateException("Refresh Token 状态写入失败");
    }

    /** @param tokenDigest HMAC 摘要而非 Token 明文 @return Token 状态 */
    public Optional<IamRefreshTokenEntity> findByDigest(String tokenDigest) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<IamRefreshTokenEntity>lambdaQuery()
                .eq(IamRefreshTokenEntity::getTokenDigest, tokenDigest)));
    }
}
