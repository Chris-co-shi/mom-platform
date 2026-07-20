package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * MOM 可更新普通业务实体的完整基础类型。
 *
 * <p>在审计字段上增加乐观锁和逻辑删除。中间表、日志、Outbox/Inbox、流水、快照、OAuth 协议表和特殊
 * 状态表不得机械继承。版本与删除初值由实体和数据库默认值约束，不由审计处理器猜测。</p>
 */
@Getter
@Setter
public abstract class BaseEntity extends BaseAuditEntity {

    /** MyBatis-Plus 乐观锁版本号，新记录从零开始。 */
    @Version
    @TableField("version")
    private Long version = 0L;

    /** 布尔逻辑删除标识：false 有效，true 已删除。 */
    @TableLogic(value = "false", delval = "true")
    @TableField("deleted")
    private Boolean deleted = Boolean.FALSE;
}
