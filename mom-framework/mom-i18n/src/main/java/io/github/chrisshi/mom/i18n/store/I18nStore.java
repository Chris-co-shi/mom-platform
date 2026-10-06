package io.github.chrisshi.mom.i18n.store;

import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;

import java.util.List;

/**
 * 当前宿主服务唯一的 I18n 持久化 SPI。
 *
 * <p>Framework 只描述用例所需的批量读和版本化写，不依赖业务 Entity/Mapper。
 * 实现只能访问本服务 Schema；写方法参加调用方本地事务，数据库故障直接传播，
 * 不查询其他服务、缓存或跨 Schema 数据。</p>
 */
public interface I18nStore {
    /** @return ID 对应的消息；不存在返回 null，forUpdate 用于同一消息译文串行化 */
    I18nMessage message(String id, boolean forUpdate);

    /** @return 指定稳定键的启用或停用消息；不存在返回 null */
    I18nMessage message(String namespace, String messageKey);

    /** @return 单 namespace 的管理列表，按 messageKey/ID 稳定排序 */
    List<I18nMessage> messages(String namespace);

    /** @return 多 namespace 中启用的消息；实现必须一次批量查询，不能逐键查询 */
    List<I18nMessage> enabledMessages(List<String> namespaces);

    /** @return 单消息全部译文，按 Locale/ID 稳定排序 */
    List<I18nTranslation> translations(String messageId);

    /** @return 给定消息与 Locale 集合的译文；空消息集合应直接返回空列表 */
    List<I18nTranslation> translations(List<String> messageIds, List<String> localeCodes);

    /** 创建消息；唯一冲突应以数据库异常向上报告。 */
    I18nMessage createMessage(String namespace, String messageKey, String description, boolean enabled);

    /** 按 version 更新说明与启停；更新行数不为一返回 false。 */
    boolean updateMessage(String id, String description, boolean enabled, long version);

    /** 创建译文；唯一冲突应以数据库异常向上报告。 */
    I18nTranslation createTranslation(String messageId, String localeCode, String messageText);

    /** 按 version 更新译文；更新行数不为一返回 false。 */
    boolean updateTranslation(String id, String messageText, long version);
}
