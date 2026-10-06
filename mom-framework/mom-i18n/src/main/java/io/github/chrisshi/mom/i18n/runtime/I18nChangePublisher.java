package io.github.chrisshi.mom.i18n.runtime;

/**
 * 公共管理用例提交译文后发出的本实例失效信号。
 *
 * <p>实现必须在事务提交后通知；回滚不能通知。星号 Locale 表示该 namespace
 * 在所有已加载语言中失效。通知失败不得回滚已提交的本地权威数据。</p>
 */
@FunctionalInterface
public interface I18nChangePublisher {
    /** 通知指定 Locale/namespace 已变化。 */
    void bundleChanged(String localeCode, String namespace);
}
