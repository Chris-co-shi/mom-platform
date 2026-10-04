package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 固定七维 SI 指数向量的量纲持久化实体，属于 MDM Infrastructure 数据库边界。
 *
 * <p>该类型仅映射单表，不承担换算算法或 HTTP 语义；向量与 Code 创建后不可普通修改，名称允许修正。
 * 并发更新由 BaseEntity 乐观锁处理，数据库不可用时调用失败且不降级。</p>
 */
@Getter
@Setter
@TableName("mdm_dimension")
public class DimensionEntity extends BaseEntity {
    private String code;
    private String name;
    private Integer timeExponent;
    private Integer lengthExponent;
    private Integer massExponent;
    private Integer electricCurrentExponent;
    private Integer temperatureExponent;
    private Integer amountExponent;
    private Integer luminousIntensityExponent;
    private String status;
}
