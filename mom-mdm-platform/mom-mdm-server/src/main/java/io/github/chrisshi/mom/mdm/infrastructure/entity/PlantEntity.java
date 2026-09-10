package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Plant 单表持久化实体，属于 MDM Infrastructure 数据库边界。
 *
 * <p>该类型只映射权威主数据行，不包含 HTTP 或 Application 语义。并发更新由 BaseEntity Version 处理，
 * 数据库不可用时写入直接失败并由 Application 本地事务回滚；V1 不暴露逻辑删除能力。</p>
 */
@Getter
@Setter
@TableName("mdm_plant")
public class PlantEntity extends BaseEntity {
    private String code;
    private String nameZh;
    private String nameEn;
    private String status;
}
