package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * MOM 需要创建与修改审计信息的实体基类。
 *
 * <p>该类型在 {@link BaseIdEntity} 之上增加 UTC 时间和操作主体，但不包含乐观锁与逻辑删除字段。
 * 只追加、不允许修改的日志表通常应自行建模创建时间；需要常规审计但不适合逻辑删除或版本控制的表，
 * 可以继承本类。这样可避免所有中间表、日志表被迫携带完整业务实体字段。</p>
 *
 * <p>时间统一使用 {@link Instant}，数据库列应使用 PostgreSQL {@code timestamptz}。操作主体允许为空，
 * 因为系统启动任务、历史迁移或尚未接入 IAM 的调用不应伪造管理员身份。</p>
 */
@Getter
@Setter
public abstract class BaseAuditEntity extends BaseIdEntity {

    /**
     * 首次持久化时间，由数据模块按 UTC 自动填充。
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 最近一次持久化更新时间，由数据模块按 UTC 自动填充。
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /**
     * 创建记录的主体标识；无法可靠确认主体时允许为空。
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /**
     * 最近一次修改记录的主体标识；无法可靠确认主体时允许为空。
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
