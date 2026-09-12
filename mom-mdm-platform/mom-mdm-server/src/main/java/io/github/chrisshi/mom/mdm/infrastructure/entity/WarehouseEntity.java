package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** MDM Infrastructure 中的 Warehouse 单表实体；不承载容量、库存或仓储策略运行时事实。 */
@Getter
@Setter
@TableName("mdm_warehouse")
public class WarehouseEntity extends BaseEntity {
    private String code;
    private String nameZh;
    private String nameEn;
    private String plantId;
    private String status;
}
