package io.github.chrisshi.mom.system.application.catalog;

import java.util.Set;

/**
 * Catalog Publish 使用的 Dynamic I18n 当前发布引用批量查询 Port。
 *
 * <p>实现只读取 System 同 Schema 的 I18n Resource/Release，不访问网络或 IAM。返回集合只包含同时存在于
 * 当前完整双 Locale 发布版本中的 Reference；Application 对缺失项 Fail Closed。</p>
 */
public interface CatalogI18nReferenceQuery {
    Set<Reference> findPublished(String applicationCode, Set<Reference> references);

    /** 稳定资源 Code 与消息 Key。 */
    record Reference(String resourceCode, String messageKey) {
    }
}
