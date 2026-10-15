package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

/** 工艺路线身份及其固定工厂，具体有序步骤只存在于修订。 */
@Getter
@Setter
@TableName("mdm_routing")
public class RoutingEntity extends BaseAuditEntity {
    private String code;
    private String name;
    private String plantId;
}
