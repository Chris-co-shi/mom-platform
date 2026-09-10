package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** MDM Infrastructure 中的 Workshop 单表实体；父级完整性由 Application 校验，数据库不建立物理外键。 */
@Getter
@Setter
@TableName("mdm_workshop")
public class WorkshopEntity extends BaseEntity {
    private String code;
    private String nameZh;
    private String nameEn;
    private String plantId;
    private String status;
}
