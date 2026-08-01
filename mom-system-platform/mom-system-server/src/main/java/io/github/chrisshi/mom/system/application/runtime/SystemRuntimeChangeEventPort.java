package io.github.chrisshi.mom.system.application.runtime;

import io.github.chrisshi.mom.system.api.ParameterScopeType;

import java.util.Map;

/**
 * System Runtime 可见状态变更的可靠事件出站 Port。
 *
 * <p>Application 只提交不含敏感正文的稳定事件数据，不依赖 Outbox、RocketMQ、StreamBridge 或 JSON 库。
 * Adapter 必须在当前 System 本地事务中追加 Outbox。</p>
 */
public interface SystemRuntimeChangeEventPort {

    /** 在当前 Catalog 发布事务中追加发布事实。 */
    void catalogPublished(CatalogPublishedEvent event);

    /** 在当前 Parameter 写事务中追加失效事实。 */
    void parameterChanged(ParameterChangedEvent event);

    /** 在当前 Dictionary 或 Item 写事务中追加失效事实。 */
    void dictionaryChanged(DictionaryChangedEvent event);

    /** 在当前 I18n 发布事务中追加发布事实。 */
    void i18nPublished(I18nPublishedEvent event);

    /** 在当前 I18n Resource 启停事务中追加 Kill Switch 变更事实。 */
    void i18nStatusChanged(I18nStatusChangedEvent event);

    enum ChangeKind {
        CREATED,
        UPDATED,
        STATUS_CHANGED
    }

    record CatalogPublishedEvent(
            String applicationId,
            String applicationCode,
            long releaseVersion,
            int routeContractVersion,
            String checksum,
            Long sourceReleaseVersion) {
    }

    record ParameterChangedEvent(
            String parameterId,
            String parameterKey,
            ParameterScopeType scopeType,
            String scopeCode,
            long version,
            boolean enabled,
            ChangeKind changeKind) {
    }

    record DictionaryChangedEvent(
            String dictionaryId,
            String dictionaryCode,
            String itemCode,
            long version,
            boolean enabled,
            ChangeKind changeKind) {
    }

    record I18nPublishedEvent(
            String resourceId,
            String applicationCode,
            String resourceCode,
            long releaseVersion,
            Map<String, String> checksums,
            Long sourceReleaseVersion) {
        public I18nPublishedEvent {
            checksums = checksums == null ? Map.of() : Map.copyOf(checksums);
        }
    }

    record I18nStatusChangedEvent(
            String resourceId,
            String applicationCode,
            String resourceCode,
            long version,
            boolean enabled) {
    }
}
