package io.github.chrisshi.mom.i18n.model;

import java.time.Instant;

/**
 * Framework 可理解的消息定义，不包含任何服务的数据库列映射。
 *
 * <p>稳定身份是宿主 namespace 与 messageKey；技术 ID 仅供当前服务管理用例定位。
 * 本记录不可变、可并发共享；数据库不可用时由 Store 显式失败。</p>
 *
 * @param id 当前服务内部技术 ID
 * @param namespace 数据 Owner 的 namespace
 * @param messageKey namespace 内不可改的稳定键
 * @param description 可选说明
 * @param enabled 是否进入 Runtime
 * @param version 乐观锁版本
 * @param updatedAt 最近修改时间
 */
public record I18nMessage(String id, String namespace, String messageKey, String description,
                          boolean enabled, long version, Instant updatedAt) {
}
