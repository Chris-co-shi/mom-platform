package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

/**
 * MOM 关系型实体的最小主键基类。
 *
 * <p>该类型只声明主键，不附带审计、乐观锁或逻辑删除能力，适合仅需要单列主键的中间表、快照表，
 * 也可作为更高层实体基类的起点。使用复合主键或完全无主键语义的表不应为了统一形式而强制继承它。</p>
 *
 * <p>主键在 Java 与对外契约中统一使用 {@link String}。MyBatis-Plus 的 {@link IdType#ASSIGN_ID}
 * 在写入前生成分布式数字 ID，并转换成字符串保存。数据库列应使用 {@code varchar(19)}，从根源上避免
 * JavaScript Number 对 64 位整数表示不精确导致的前端 ID 截断或舍入问题。该 ID 是技术主键，不替代
 * 物料编码、工单号等领域业务标识。</p>
 */
@Getter
@Setter
public abstract class BaseIdEntity {

    /**
     * 应用侧生成的字符串技术主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;
}
