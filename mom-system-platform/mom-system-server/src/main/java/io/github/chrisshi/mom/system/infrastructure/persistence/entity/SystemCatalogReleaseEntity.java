package io.github.chrisshi.mom.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.data.typehandler.PostgresqlJsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

/**
 * Catalog 不可变 Release 的 MyBatis-Plus 行模型。
 *
 * <p>实体只继承 String ID 与创建/更新审计，不声明 Version、逻辑删除或普通更新能力。JSONB 只接受受控
 * Snapshot Codec 输出；数据库 Trigger 拒绝 UPDATE/DELETE。</p>
 */
@Getter
@Setter
@TableName(value = "system_catalog_release", autoResultMap = true)
public class SystemCatalogReleaseEntity extends BaseAuditEntity {
    @TableField("application_id") private String applicationId;
    @TableField("application_code") private String applicationCode;
    @TableField("release_version") private Long releaseVersion;
    @TableField("snapshot_schema_version") private Integer snapshotSchemaVersion;
    @TableField("route_contract_version") private Integer routeContractVersion;
    @TableField("source_application_version") private Long sourceApplicationVersion;
    @TableField("source_release_version") private Long sourceReleaseVersion;
    @TableField(value = "snapshot_json", typeHandler = PostgresqlJsonbStringTypeHandler.class)
    private String snapshotJson;
    @TableField("node_count") private Integer nodeCount;
    @TableField("checksum") private String checksum;
    @TableField("change_note") private String changeNote;
}
