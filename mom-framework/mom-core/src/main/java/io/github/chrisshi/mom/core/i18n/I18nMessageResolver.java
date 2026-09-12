package io.github.chrisshi.mom.core.i18n;

import java.util.Locale;

/**
 * 服务端错误消息的最小国际化解析契约。
 *
 * <p>该接口位于无 Web、数据库和 Spring 依赖的 Core 边界，使 MVC Exception Handler 与 Servlet Security
 * Filter Chain 可以复用完全一致的解析语义。实现方自行决定 classpath 或本服务数据库资源；本契约不统一数据
 * 所有权、不允许跨服务同步查询，也不承诺缓存。实现必须是线程安全的；资源不可用时按实现所属边界显式失败或
 * 回退到稳定 messageKey，不能改变稳定错误 code。</p>
 */
@FunctionalInterface
public interface I18nMessageResolver {

    /**
     * 将稳定消息键解析为最终展示文本。
     *
     * @param messageKey 不本地化的稳定消息键
     * @param locale 请求展示 Locale；实现不得依赖宿主机默认 Locale
     * @param args MessageFormat 数字 Placeholder 参数
     * @return 本地化展示文本；资源缺失时至少返回稳定 messageKey
     */
    String resolve(String messageKey, Locale locale, Object... args);
}
