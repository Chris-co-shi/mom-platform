package io.github.chrisshi.mom.webmvc.i18n;

import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.webmvc.i18n.I18nRuntimeProvider.I18nRuntimeBundle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 可由任意 Servlet 业务服务复用的薄 I18n Runtime HTTP Controller。
 *
 * <p>路径前缀由 {@code mom.i18n.runtime.base-path} 配置，Framework 不硬编码 System、MDM、MES 等 Owner。
 * Controller 只做输入上限、去重和 Provider 调用，不访问数据库或决定 namespace ownership。认证与授权由
 * 宿主服务的 SecurityFilterChain 负责。</p>
 */
@RestController
@RequestMapping("${mom.i18n.runtime.base-path:/i18n/runtime}")
public final class I18nRuntimeController {
    private static final int MAX_NAMESPACES = 20;
    private final I18nRuntimeProvider provider;

    /** @param provider 宿主服务提供的本地 Bundle Provider */
    public I18nRuntimeController(I18nRuntimeProvider provider) {
        this.provider = provider;
    }

    /**
     * 加载一个 Locale 下的多个 namespace Bundle。
     *
     * @param locale BCP 47 Locale Tag
     * @param namespaces 一个或多个 namespace，最多 20 个
     * @return Provider 已完成默认 Locale fallback 的 Bundle
     */
    @GetMapping("/bundles")
    public Result<I18nRuntimeBundle> bundles(
            @RequestParam String locale,
            @RequestParam List<String> namespaces) {
        List<String> normalized = List.copyOf(new LinkedHashSet<>(namespaces));
        if (normalized.isEmpty() || normalized.size() > MAX_NAMESPACES) {
            throw new IllegalArgumentException("namespaces 数量必须在 1～" + MAX_NAMESPACES + " 之间");
        }
        return Result.success(provider.load(locale, normalized));
    }
}
