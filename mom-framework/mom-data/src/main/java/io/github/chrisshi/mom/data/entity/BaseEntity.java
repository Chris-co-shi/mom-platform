package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * MOM 可更新业务实体的完整基础类型。
 *
 * <p>该类型在 {@link BaseAuditEntity} 之上增加乐观锁与逻辑删除，适用于存在更新并发、需要保留历史引用
 * 且允许业务侧“删除”的普通领域表。中间表、日志表、Outbox、流水和快照表不应默认继承本类，应根据
 * 实际字段选择 {@link BaseIdEntity}、{@link BaseAuditEntity} 或完全独立建模。</p>
 *
 * <p>逻辑删除只改变 {@code deleted} 标记，不代表数据物理清理、归档或法务保留策略。唯一索引是否需要
 * 包含删除标记必须由各领域迁移脚本单独设计，Framework 不对唯一键复用做隐式假设。</p>
 */
@Getter
@Setter
public abstract class BaseEntity extends BaseAuditEntity {

    /**
     * MyBatis-Plus 乐观锁版本号，插入时从零开始。
     */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Long version;

    /**
     * 逻辑删除标识：{@code false} 表示有效，{@code true} 表示已删除。
     */
    @TableLogic(value = "false", delval = "true")
    @TableField(value = "deleted", fill = FieldFill.INSERT)
    private Boolean deleted;
}
