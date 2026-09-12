package io.github.chrisshi.mom.webmvc.i18n;

import java.util.List;
import java.util.Map;

/**
 * 业务服务向 Framework Runtime Controller 提供本地 I18n Bundle 的契约。
 *
 * <p>Provider 由数据 Owner 在自己的服务中实现，Framework 不知道数据库、namespace 清单或回退规则的
 * 技术细节。实现必须只读取本服务权威数据，不跨服务查询；数据库不可用时应显式失败，不能返回伪造的
 * 空成功。读取无副作用且应支持并发调用。</p>
 */
@FunctionalInterface
public interface I18nRuntimeProvider {

    /**
     * 按 Locale 和 namespace 集合加载可直接供客户端使用的 Bundle。
     *
     * @param localeCode BCP 47 Locale Tag
     * @param namespaces 有界、去重后的 namespace 列表
     * @return 包含实际生效 Locale 和各 namespace 文案的不可变结果
     * @throws IllegalArgumentException Locale 或 namespace 输入非法
     */
    I18nRuntimeBundle load(String localeCode, List<String> namespaces);

    /**
     * Runtime Bundle 的稳定 Framework 结果。
     *
     * @param requestedLocale 请求 Locale
     * @param effectiveLocale 实际生效 Locale；请求不存在或禁用时为默认 Locale
     * @param bundles namespace 到 messageKey/text 的只读映射
     */
    record I18nRuntimeBundle(
            String requestedLocale,
            String effectiveLocale,
            Map<String, Map<String, String>> bundles) {
        /**
         * 创建深度只读的运行时结果，阻止 Controller 返回后被调用方并发修改。
         *
         * @throws NullPointerException bundles、namespace 或内部 Map 为空时拒绝构造
         */
        public I18nRuntimeBundle {
            bundles = bundles.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry -> Map.copyOf(entry.getValue())));
        }
    }
}
