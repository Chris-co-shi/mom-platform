package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 只需要 String 主键和创建审计的关系型实体基类。 */
@Getter
@Setter
public abstract class BaseCreatedEntity extends BaseIdEntity {

    /** 记录首次持久化的 UTC 时间，由服务端强制填充，UPDATE 永不写回。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    /** 创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code，UPDATE 永不写回。 */
    @TableField(value = "created_by", fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private String createdBy;
}
