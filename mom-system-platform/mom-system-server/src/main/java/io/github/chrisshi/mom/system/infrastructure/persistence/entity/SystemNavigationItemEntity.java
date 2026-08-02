package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import lombok.Getter;
import lombok.Setter;

/** System Navigation Draft 的 MyBatis-Plus 行模型。 */
@Getter
@Setter
@TableName("system_navigation_item")
public class SystemNavigationItemEntity extends BaseAuditEntity {
    @TableField("application_id") private String applicationId;
    @TableField(value = "parent_id", updateStrategy = FieldStrategy.ALWAYS) private String parentId;
    @TableField("client_channel") private ClientChannel clientChannel;
    @TableField("navigation_type") private NavigationType navigationType;
    @TableField("route_key") private String routeKey;
    @TableField("i18n_resource_code") private String i18nResourceCode;
    @TableField("i18n_message_key") private String i18nMessageKey;
    @TableField(value = "permission_code", updateStrategy = FieldStrategy.ALWAYS) private String permissionCode;
    @TableField(value = "icon_key", updateStrategy = FieldStrategy.ALWAYS) private String iconKey;
    @TableField("visible_in_menu") private Boolean visibleInMenu;
    @TableField("visible_in_breadcrumb") private Boolean visibleInBreadcrumb;
    @TableField("visible_in_tab") private Boolean visibleInTab;
    @TableField("keep_alive") private Boolean keepAlive;
    @TableField("sort_order") private Integer sortOrder;
    @TableField("enabled") private Boolean enabled;
    @Version
    @TableField("version") private Long version = 0L;
}
