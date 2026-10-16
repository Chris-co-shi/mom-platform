package io.github.chrisshi.mom.webmvc.i18n;

import io.github.chrisshi.mom.i18n.model.I18nBundle;
import io.github.chrisshi.mom.i18n.runtime.I18nRuntimeService;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 可由任意 Servlet 业务服务复用的薄 I18n Runtime HTTP Controller。
 *
 * <p>路径固定为 {@code /i18n/runtime}，Framework 不硬编码 System、MDM、MES 等 Owner。
 * Controller 只适配 Locale 并调用公共 Runtime Service，不访问数据库或决定 Owner。认证与授权由
 * 宿主服务的 SecurityFilterChain 负责。</p>
 */
@RestController
@RequestMapping("/i18n/runtime")
public final class I18nRuntimeController {
    private final I18nRuntimeService runtime;

    /** @param runtime Framework 公共 Bundle 用例 */
    public I18nRuntimeController(I18nRuntimeService runtime) {
        this.runtime = runtime;
    }

    /**
     * 加载当前服务 Owner 一个 Locale 的完整扁平 Bundle。
     *
     * @param locale BCP 47 Locale Tag
     * @return 公共用例已完成本地 base Locale fallback 的 Bundle
     */
    @GetMapping("/bundles")
    public Result<I18nBundle> bundles(@RequestParam String locale) {
        return Result.success(runtime.load(locale));
    }
}
