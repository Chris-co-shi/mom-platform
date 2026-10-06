package io.github.chrisshi.mom.i18n.model;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runtime 向 Web 返回的 namespace 分组译文。
 *
 * <p>构造时深度复制，避免并发请求之间共享可变 Map。缺失译文的值是稳定 messageKey，
 * 不返回数据库内部 ID 或审计字段。</p>
 *
 * @param requestedLocale 客户端请求的 Locale
 * @param effectiveLocale 校验后的实际 Locale
 * @param bundles namespace 到 messageKey/text 的映射
 */
public record I18nBundle(String requestedLocale, String effectiveLocale,
                         Map<String, Map<String, String>> bundles) {
    /** @throws NullPointerException 输入 Map 含空键或值时拒绝构造 */
    public I18nBundle {
        bundles = bundles.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    }
}
