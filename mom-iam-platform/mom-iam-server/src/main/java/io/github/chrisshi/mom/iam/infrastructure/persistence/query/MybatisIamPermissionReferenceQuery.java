package io.github.chrisshi.mom.iam.infrastructure.persistence.query;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.iam.application.permissionreference.port.IamPermissionReferenceQueryPort;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamPermissionEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamPermissionMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 MyBatis-Plus 的 Permission Code 批量只读 Adapter。
 *
 * <p>查询使用单次参数化 IN 条件，只读取 Code 与状态，不新增 Mapper XML、注解 SQL、JdbcTemplate 或逐条
 * N+1 查询。</p>
 */
public final class MybatisIamPermissionReferenceQuery implements IamPermissionReferenceQueryPort {
    private final IamPermissionMapper mapper;

    public MybatisIamPermissionReferenceQuery(IamPermissionMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Map<String, IamRecordStatus> findStatusesByCodes(Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return Map.of();
        }
        var query = Wrappers.<IamPermissionEntity>lambdaQuery()
                .select(IamPermissionEntity::getCode, IamPermissionEntity::getStatus)
                .in(IamPermissionEntity::getCode, permissionCodes);
        Map<String, IamRecordStatus> result = new LinkedHashMap<>();
        mapper.selectList(query).forEach(value -> result.put(value.getCode(), value.getStatus()));
        return Map.copyOf(result);
    }
}
