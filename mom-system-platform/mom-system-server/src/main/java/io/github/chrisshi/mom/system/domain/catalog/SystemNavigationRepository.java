package io.github.chrisshi.mom.system.domain.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** System Navigation Draft 单表领域持久化端口。 */
public interface SystemNavigationRepository {
    Optional<SystemNavigationItem> findById(String id);
    List<SystemNavigationItem> findByApplication(String applicationId);
    List<SystemNavigationItem> findByApplicationAndChannel(String applicationId, ClientChannel channel);
    List<SystemNavigationItem> findByIds(Collection<String> ids);
    SystemNavigationItem insert(SystemNavigationItem item);
    boolean update(SystemNavigationItem item);
    boolean updateStatus(SystemNavigationItem item);
}
