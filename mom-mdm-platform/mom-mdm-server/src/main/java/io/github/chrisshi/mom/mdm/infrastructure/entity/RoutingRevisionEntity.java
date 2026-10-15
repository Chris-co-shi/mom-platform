package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Routing 的业务修订与独立技术锁；发布后步骤不可修改。 */
@Getter
@Setter
@TableName("mdm_routing_revision")
public class RoutingRevisionEntity extends BaseAuditEntity {
    private String routingId;
    private Integer revisionNo;
    private String status;
    @Version
    @TableField("lock_version")
    private Long lockVersion = 0L;
    private Instant releasedAt;
    private Instant obsoleteAt;
}
