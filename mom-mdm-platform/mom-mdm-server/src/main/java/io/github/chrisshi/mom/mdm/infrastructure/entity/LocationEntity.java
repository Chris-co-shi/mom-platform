package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * GEO 厂内统一可寻址位置的持久化实体。
 *
 * <p>Plant 与 LocationType 必填，WarehouseArea 可空。该实体不记录占用、预留、库存、容器或 AGV 事实；
 * 引用存在性和 WarehouseArea 与 Plant 的一致性由 MDM Application 在本地事务中校验。</p>
 */
@Getter
@Setter
@TableName("mdm_location")
public class LocationEntity extends BaseEntity {
    private String code;
    private String nameZh;
    private String nameEn;
    private String plantId;
    private String warehouseAreaId;
    private String locationTypeId;
    private String status;
}
