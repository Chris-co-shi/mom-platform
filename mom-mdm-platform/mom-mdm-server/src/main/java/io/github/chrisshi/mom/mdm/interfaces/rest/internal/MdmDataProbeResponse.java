package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.mdm.infrastructure.persistence.MdmDataProbeEntity;

import java.time.Instant;

/**
 * MDM PostgreSQL 技术探针响应。
 *
 * @param id 字符串技术主键；前端不得转换为 JavaScript Number
 * @param probeKey 技术验证键
 * @param probeValue 技术验证值
 * @param createdAt UTC 创建时间
 * @param updatedAt UTC 最近更新时间
 * @param createdBy 创建主体；未接入可靠主体时为空
 * @param updatedBy 最近修改主体；未接入可靠主体时为空
 * @param version 乐观锁版本号
 * @param deleted 逻辑删除标识
 */
public record MdmDataProbeResponse(
        String id,
        String probeKey,
        String probeValue,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version,
        Boolean deleted) {

    /**
     * 将持久化实体转换为内部技术接口响应，避免 Controller 直接暴露数据库实体。
     *
     * @param entity MDM 技术验证实体
     * @return 与实体当前状态对应的响应
     */
    public static MdmDataProbeResponse from(MdmDataProbeEntity entity) {
        return new MdmDataProbeResponse(
                entity.getId(),
                entity.getProbeKey(),
                entity.getProbeValue(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                entity.getVersion(),
                entity.getDeleted());
    }
}
