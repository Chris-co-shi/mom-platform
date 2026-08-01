package io.github.chrisshi.mom.system.application.runtime;

/**
 * System Runtime 可见状态变更的可靠事件出站 Port。
 *
 * <p>Application 只提交不含敏感正文的稳定事件数据，不依赖 Outbox、RocketMQ、StreamBridge 或 JSON 库。
 * Adapter 必须在当前 System 本地事务中追加 Outbox。</p>
 */
public interface SystemRuntimeChangeEventPort {

    /** 在当前 Catalog 发布事务中追加发布事实。 */
    void catalogPublished(CatalogPublishedEvent event);

    record CatalogPublishedEvent(
            String applicationId,
            String applicationCode,
            long releaseVersion,
            int routeContractVersion,
            String checksum,
            Long sourceReleaseVersion) {
    }
}
