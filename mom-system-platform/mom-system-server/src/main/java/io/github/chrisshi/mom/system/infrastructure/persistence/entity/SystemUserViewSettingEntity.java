package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.data.typehandler.PostgresqlJsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * System 用户受限视图设置的 MyBatis-Plus 行模型。
 *
 * <p>JSONB 字段只接收受控 Codec 生成的类型化数组，不能从 Web 直接写入。实体不带逻辑删除；Reset 使用
 * enabled=false 与空数组。唯一性和 Version 由 PostgreSQL/MyBatis-Plus 共同保证。</p>
 */
@Getter
@Setter
@TableName(value = "system_user_view_setting", autoResultMap = true)
public class SystemUserViewSettingEntity extends BaseAuditEntity {
    @TableField("user_id")
    private String userId;

    @TableField("application_code")
    private String applicationCode;

    @TableField("view_key")
    private String viewKey;

    @TableField("schema_version")
    private Integer schemaVersion;

    @TableField(value = "columns_json", typeHandler = PostgresqlJsonbStringTypeHandler.class)
    private String columnsJson;

    @TableField(value = "sort_json", typeHandler = PostgresqlJsonbStringTypeHandler.class)
    private String sortJson;

    @TableField(value = "filters_json", typeHandler = PostgresqlJsonbStringTypeHandler.class)
    private String filtersJson;

    @TableField(value = "page_size", updateStrategy = FieldStrategy.ALWAYS)
    private Integer pageSize;

    @TableField("enabled")
    private Boolean enabled;

    @Version
    @TableField("version")
    private Long version = 0L;
}
