package io.github.chrisshi.mom.data.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.Instant;

/**
 * MOM 关系型数据实体的最小审计基类。
 *
 * <p>该基类只包含跨领域稳定的数据字段：数据库自增主键、UTC 审计时间、可选操作主体和乐观锁版本。
 * 领域状态、软删除、租户、组织和业务编号不属于通用基础设施，必须由具体领域实体自行建模，避免
 * Framework 把尚未确认的业务规则强加给 MES、WMS、QMS 或 MDM。</p>
 *
 * <p>时间统一使用 {@link Instant}，数据库列应使用 PostgreSQL {@code timestamptz}。应用展示时再根据
 * 业务时区转换，禁止在实体中保存服务器本地时间。该类型不实现 Java 序列化，避免实体被误用为缓存
 * 或远程传输契约。</p>
 */
public abstract class BaseEntity {

    /**
     * 数据库生成的内部主键。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 首次持久化时间，统一由数据模块按 UTC 自动填充。
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 最近一次持久化更新时间，统一由数据模块按 UTC 自动填充。
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /**
     * 创建该记录的主体标识。无法可靠确认主体时允许为空。
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    /**
     * 最近一次修改该记录的主体标识。无法可靠确认主体时允许为空。
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /**
     * MyBatis-Plus 乐观锁版本号，插入时从零开始。
     */
    @Version
    @TableField(value = "version", fill = FieldFill.INSERT)
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
