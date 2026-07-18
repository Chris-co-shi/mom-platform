package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeEntity;
import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * MDM PostgreSQL 技术验证事务服务。
 *
 * <p>该服务显式控制事务边界，不继承 MyBatis-Plus {@code IService/ServiceImpl}。这样可以避免把数据库
 * 通用 CRUD 方法直接升级为领域服务契约，并使后续主数据聚合能够围绕业务动作定义事务。</p>
 */
public class MdmDataProbeService {

    private static final int MAX_KEY_LENGTH = 120;
    private static final int MAX_VALUE_LENGTH = 500;
    private static final int MAX_ID_LENGTH = 19;

    private final MdmDataProbeMapper mapper;

    /**
     * 创建数据技术验证服务。
     *
     * @param mapper MDM 技术验证 Mapper
     */
    public MdmDataProbeService(MdmDataProbeMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 创建一条技术验证记录。
     *
     * @param probeKey 稳定业务键
     * @param probeValue 验证值
     * @return 插入后包含字符串主键和审计字段的实体
     * @throws IllegalArgumentException 参数为空或超过数据库长度约束时抛出
     */
    @Transactional
    public MdmDataProbeEntity create(String probeKey, String probeValue) {
        MdmDataProbeEntity entity = new MdmDataProbeEntity();
        entity.setProbeKey(requireText(probeKey, "probeKey", MAX_KEY_LENGTH));
        entity.setProbeValue(requireText(probeValue, "probeValue", MAX_VALUE_LENGTH));
        mapper.insert(entity);
        return entity;
    }

    /**
     * 根据技术验证键读取未删除记录。
     *
     * <p>实体声明了 MyBatis-Plus 逻辑删除字段，因此框架会自动在查询条件中追加
     * {@code deleted = false}，已删除记录不会进入普通读取结果。</p>
     *
     * @param probeKey 技术验证键
     * @return 匹配的有效记录；不存在或已逻辑删除时返回空
     */
    @Transactional(readOnly = true)
    public Optional<MdmDataProbeEntity> findByKey(String probeKey) {
        String normalizedKey = requireText(probeKey, "probeKey", MAX_KEY_LENGTH);
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<MdmDataProbeEntity>lambdaQuery()
                        .eq(MdmDataProbeEntity::getProbeKey, normalizedKey)));
    }

    /**
     * 使用调用方持有的版本号更新验证值。
     *
     * <p>MyBatis-Plus 乐观锁会在 SQL 条件中附加旧版本号，并在成功时递增版本。返回 {@code false}
     * 表示记录不存在、已逻辑删除或已经被其他事务修改，调用方不得把它当作成功覆盖。</p>
     *
     * @param id 字符串技术主键
     * @param expectedVersion 调用方读取到的版本号
     * @param probeValue 新验证值
     * @return 更新成功返回 {@code true}；版本冲突或记录不可更新时返回 {@code false}
     * @throws IllegalArgumentException ID、版本号或验证值不合法时抛出
     */
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

    /**
     * 按主键执行逻辑删除。
     *
     * <p>该操作不会物理删除行。MyBatis-Plus 会把 {@code deleted} 更新为 {@code true}，后续普通查询、
     * 更新和重复删除均不会再命中该记录。</p>
     *
     * @param id 字符串技术主键
     * @return 首次成功标记删除返回 {@code true}；记录不存在或已经删除返回 {@code false}
     */
    @Transactional
    public boolean deleteById(String id) {
        return mapper.deleteById(requireText(id, "id", MAX_ID_LENGTH)) == 1;
    }

    /**
     * 插入记录后主动抛出异常，用于验证 Spring 事务回滚。
     *
     * @param probeKey 技术验证键
     * @param probeValue 技术验证值
     * @throws IllegalStateException 始终抛出以触发当前事务回滚
     */
    @Transactional
    public void createThenRollback(String probeKey, String probeValue) {
        create(probeKey, probeValue);
        throw new IllegalStateException("P01-S04 主动触发事务回滚");
    }

    /**
     * 校验文本非空并与数据库列长度保持一致，避免依赖数据库异常表达参数错误。
     *
     * @param value 待校验文本
     * @param fieldName 参数名称
     * @param maxLength 最大长度
     * @return 去除首尾空白后的文本
     */
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
