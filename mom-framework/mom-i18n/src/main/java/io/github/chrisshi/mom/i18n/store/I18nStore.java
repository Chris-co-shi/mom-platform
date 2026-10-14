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
    I18nMessage message(String messageKey);

    /**
     * 读取当前 Schema 的管理文案，不查询其他服务。
     * @return 按 messageKey/ID 稳定排序的启用及停用文案
     * 数据库故障直接传播；只读、可重复调用，没有写副作用。
     */
    List<I18nMessage> messages();

    /** @return 当前 Owner 中启用的消息；实现必须一次批量查询，不能逐键查询 */
    List<I18nMessage> enabledMessages();

    /** @return 单消息全部译文，按 Locale/ID 稳定排序 */
    List<I18nTranslation> translations(String messageId);

    /** @return 给定消息与 Locale 集合的译文；空消息集合应直接返回空列表 */
    List<I18nTranslation> translations(List<String> messageIds, List<String> localeCodes);

    /** 创建消息；唯一冲突应以数据库异常向上报告。 */
    I18nMessage createMessage(String messageKey, String description, boolean enabled);

    /** @return 同 Locale、精确文本相同且属于其他稳定 Key 的 Key 列表；仅用于用户提醒 */
    List<String> duplicateMessageKeys(String currentMessageId, String localeCode, String messageText);

    /** 按 version 更新说明与启停；更新行数不为一返回 false。 */
    boolean updateMessage(String id, String description, boolean enabled, long version);

    /** 创建译文；唯一冲突应以数据库异常向上报告。 */
    I18nTranslation createTranslation(String messageId, String localeCode, String messageText);

    /** 按 version 更新译文；更新行数不为一返回 false。 */
    boolean updateTranslation(String id, String messageText, long version);
}
