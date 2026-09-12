package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** MDM Infrastructure 中的动态 LocationType 单表实体；Code 只作分类标识，不承载 Capability 分支。 */
@Getter
@Setter
@TableName("mdm_location_type")
public class LocationTypeEntity extends BaseEntity {
    private String code;
    private String nameZh;
    private String nameEn;
    private String status;
}
