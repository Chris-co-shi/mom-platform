package io.github.chrisshi.mom.i18n.namespace;

/**
 * 宿主服务声明自身可管理的 namespace，不由 Framework 猜测数据 Owner。
 *
 * <p>实现必须线程安全且无远程依赖；不属于本服务的 namespace 在读写入口均被拒绝。</p>
 */
@FunctionalInterface
public interface I18nNamespacePolicy {
    /** @return 当前宿主是否拥有该完整 namespace */
    boolean owns(String namespace);
}
