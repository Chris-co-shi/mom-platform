package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** MDM Infrastructure 中的 WarehouseArea 单表实体；父级引用由 Application 显式维护。 */
@Getter
@Setter
@TableName("mdm_warehouse_area")
public class WarehouseAreaEntity extends BaseEntity {
    private String code;
    private String nameZh;
    private String nameEn;
    private String warehouseId;
    private String status;
}
