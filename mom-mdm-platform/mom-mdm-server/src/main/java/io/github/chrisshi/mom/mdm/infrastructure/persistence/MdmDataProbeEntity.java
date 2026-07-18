package io.github.chrisshi.mom.mdm.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;

/**
 * MDM PostgreSQL 技术验证实体。
 *
 * <p>该实体只用于 P01-S04 验证 Flyway、MyBatis-Plus、UTC 审计填充、乐观锁和事务回滚，不代表
 * 物料、工厂、单位或其他正式主数据模型。完成后续领域建模时不得复用该表承载业务数据。</p>
 */
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

    public String getProbeKey() {
        return probeKey;
    }

    public void setProbeKey(String probeKey) {
        this.probeKey = probeKey;
    }

    public String getProbeValue() {
        return probeValue;
    }

    public void setProbeValue(String probeValue) {
        this.probeValue = probeValue;
    }
}
