package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 同时需要创建与修改审计的实体基类。
 *
 * <p>在 BaseCreatedEntity 上增加最近更新时间和修改 Actor，不包含乐观锁或逻辑删除。普通写入缺少 Actor
 * 时默认 fail-closed。</p>
 */
@Getter
@Setter
public abstract class BaseAuditEntity extends BaseCreatedEntity {

    /** 最近一次持久化修改的 UTC 时间，由服务端在 INSERT/UPDATE 强制覆盖。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 最近修改 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code。 */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
