package io.github.chrisshi.mom.system.domain.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;

import java.util.List;
import java.util.Optional;

/** System Application 单表领域持久化端口。 */
public interface SystemApplicationRepository {
    Optional<SystemApplication> findById(String id);
    Optional<SystemApplication> findByCode(String applicationCode);
    SystemApplication insert(SystemApplication application);
    boolean update(SystemApplication application);
    boolean updateStatus(SystemApplication application);
    boolean touch(SystemApplication application);
    boolean updatePublished(SystemApplication application);
    ApplicationPage findPage(ApplicationQuery query);
    List<SystemApplication> findEnabledPublished();

    /** 有限精确分页条件。 */
    record ApplicationQuery(
            String applicationCode,
            ApplicationType applicationType,
            Boolean enabled,
            int page,
            int size) {
    }

    /** Infrastructure 无关分页结果。 */
    record ApplicationPage(List<SystemApplication> items, long total, int page, int size) {
        public ApplicationPage {
            items = List.copyOf(items);
        }
    }
}
