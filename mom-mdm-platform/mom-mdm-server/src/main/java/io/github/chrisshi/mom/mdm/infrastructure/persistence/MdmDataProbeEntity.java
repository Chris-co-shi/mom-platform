package io.github.chrisshi.mom.mdm.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * MDM PostgreSQL 技术验证实体。
 *
 * <p>该实体只用于验证 Flyway、MyBatis-Plus、字符串主键、UTC 审计填充、逻辑删除、乐观锁和事务回滚，
 * 不代表物料、工厂、单位或其他正式主数据模型。完成后续领域建模时不得复用该表承载业务数据。</p>
 */
@Getter
@Setter
@TableName("technical_data_probe")
public class MdmDataProbeEntity extends BaseEntity {

    /**
     * 技术验证记录的稳定业务键。
     */
    private String probeKey;

    /**
     * 技术验证记录值。
     */
    private String probeValue;
}
