package io.github.chrisshi.mom.system.domain.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Catalog 不可变 Release 的追加写与查询端口。 */
public interface SystemCatalogReleaseRepository {
    Optional<SystemCatalogRelease> findById(String id);
    Optional<SystemCatalogRelease> findByApplicationAndVersion(String applicationId, long releaseVersion);
    List<SystemCatalogRelease> findByIds(Collection<String> ids);
    long nextVersion(String applicationId);
    SystemCatalogRelease insert(SystemCatalogRelease release);
    ReleasePage findHistory(String applicationId, int page, int size);

    /** Release 历史分页结果。 */
    record ReleasePage(List<SystemCatalogRelease> items, long total, int page, int size) {
        public ReleasePage {
            items = List.copyOf(items);
        }
    }
}
