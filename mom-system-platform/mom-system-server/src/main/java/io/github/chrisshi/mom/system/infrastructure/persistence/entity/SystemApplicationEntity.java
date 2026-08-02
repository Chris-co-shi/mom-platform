package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import lombok.Getter;
import lombok.Setter;

/** System Application Catalog 的 MyBatis-Plus 行模型。 */
@Getter
@Setter
@TableName("system_application")
public class SystemApplicationEntity extends BaseAuditEntity {
    @TableField("application_code") private String applicationCode;
    @TableField("application_type") private ApplicationType applicationType;
    @TableField("i18n_resource_code") private String i18nResourceCode;
    @TableField("i18n_message_key") private String i18nMessageKey;
    @TableField(value = "icon_key", updateStrategy = FieldStrategy.ALWAYS) private String iconKey;
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS) private String description;
    @TableField("route_contract_version") private Integer routeContractVersion;
    @TableField("sort_order") private Integer sortOrder;
    @TableField("enabled") private Boolean enabled;
    @TableField(value = "published_release_id", updateStrategy = FieldStrategy.ALWAYS) private String publishedReleaseId;
    @TableField("published_version") private Long publishedVersion;
    @Version
    @TableField("version") private Long version = 0L;
}
