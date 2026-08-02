package io.github.chrisshi.mom.iam.application.permissionreference.port;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;

import java.util.Collection;
import java.util.Map;

/**
 * Permission Code 权威状态只读 Port。
 *
 * <p>Application 只依赖 Code 到状态的稳定映射，不依赖 Mapper、Entity 或分页管理投影。缺失 Code 不进入返回
 * Map，由 Application 映射为 UNKNOWN。</p>
 */
public interface IamPermissionReferenceQueryPort {

    /** 单次批量查询输入 Code 中实际存在的 Permission 状态。 */
    Map<String, IamRecordStatus> findStatusesByCodes(Collection<String> permissionCodes);
}
