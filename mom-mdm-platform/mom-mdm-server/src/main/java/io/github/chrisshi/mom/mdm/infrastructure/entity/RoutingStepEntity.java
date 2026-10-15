package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseCreatedEntity;
import lombok.Getter;
import lombok.Setter;

/** 某 Routing 修订中的有序静态工序；不携带 MES 执行时间或设备状态。 */
@Getter
@Setter
@TableName("mdm_routing_step")
public class RoutingStepEntity extends BaseCreatedEntity {
    private String revisionId;
    private Integer sequenceNo;
    private String operationCode;
    private String name;
    private String resourceType;
    private String resourceId;
}
