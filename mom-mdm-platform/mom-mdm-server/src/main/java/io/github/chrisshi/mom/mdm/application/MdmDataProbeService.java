package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeEntity;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** MDM PostgreSQL 技术验证事务服务，所有普通写路径均携带非空 Entity 触发审计。 */
public class MdmDataProbeService {

    private static final int MAX_KEY_LENGTH = 120;
    private static final int MAX_VALUE_LENGTH = 500;
    private static final int MAX_ID_LENGTH = 19;
    private final MdmDataProbeMapper mapper;

    public MdmDataProbeService(MdmDataProbeMapper mapper) { this.mapper = mapper; }

    /** 创建记录并重新读取数据库默认 version/deleted 字段。 */
    @Transactional
    public MdmDataProbeEntity create(String probeKey, String probeValue) {
        MdmDataProbeEntity entity = new MdmDataProbeEntity();
        entity.setProbeKey(requireText(probeKey, "probeKey", MAX_KEY_LENGTH));
        entity.setProbeValue(requireText(probeValue, "probeValue", MAX_VALUE_LENGTH));
        mapper.insert(entity);
        return mapper.selectById(entity.getId());
    }

    /** 根据技术验证键读取未删除记录。 */
    @Transactional(readOnly = true)
    public Optional<MdmDataProbeEntity> findByKey(String probeKey) {
        String normalizedKey = requireText(probeKey, "probeKey", MAX_KEY_LENGTH);
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<MdmDataProbeEntity>lambdaQuery()
                        .eq(MdmDataProbeEntity::getProbeKey, normalizedKey)));
    }

    /** 使用调用方持有版本执行 updateById 乐观锁更新。 */
    @Transactional
    public boolean updateValue(String id, Long expectedVersion, String probeValue) {
        String normalizedId = requireText(id, "id", MAX_ID_LENGTH);
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion 不能小于零");
        }
        MdmDataProbeEntity entity = new MdmDataProbeEntity();
        entity.setId(normalizedId);
        entity.setVersion(expectedVersion);
        entity.setProbeValue(requireText(probeValue, "probeValue", MAX_VALUE_LENGTH));
        return mapper.updateById(entity) == 1;
    }

    /** 使用非空 Entity 与 Wrapper 更新，用于验证该路径能够触发审计填充。 */
    @Transactional
    public boolean updateValueByKey(String probeKey, String probeValue) {
        String normalizedKey = requireText(probeKey, "probeKey", MAX_KEY_LENGTH);
        MdmDataProbeEntity entity = new MdmDataProbeEntity();
        entity.setProbeValue(requireText(probeValue, "probeValue", MAX_VALUE_LENGTH));
        return mapper.update(entity, Wrappers.<MdmDataProbeEntity>lambdaUpdate()
                .eq(MdmDataProbeEntity::getProbeKey, normalizedKey)) == 1;
    }

    /** 按主键逻辑删除；MyBatis-Plus 创建填充 Entity，因此仍要求 Actor 并写更新审计。 */
    @Transactional
    public boolean deleteById(String id) {
        return mapper.deleteById(requireText(id, "id", MAX_ID_LENGTH)) == 1;
    }

    /** 插入后主动抛出异常，用于验证事务回滚。 */
    @Transactional
    public void createThenRollback(String probeKey, String probeValue) {
        create(probeKey, probeValue);
        throw new IllegalStateException("P01-S04 主动触发事务回滚");
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }
}
