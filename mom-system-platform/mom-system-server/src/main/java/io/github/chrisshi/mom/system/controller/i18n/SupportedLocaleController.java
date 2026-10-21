package io.github.chrisshi.mom.system.controller.i18n;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.system.api.SupportedLocaleInfo;
import io.github.chrisshi.mom.system.application.i18n.SupportedLocaleApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对外只读 Locale 目录 HTTP 边界。
 *
 * <p>Controller 不接收用户偏好或业务数据，只管理平台可选择 Locale 与唯一默认值。跨服务读取只暴露
 * localeCode；目录维护由独立 LocaleCatalogController 负责。</p>
 */
@RestController
@RequestMapping("/i18n/locales")
@RequiredArgsConstructor
public class SupportedLocaleController {
    private final SupportedLocaleApplication application;

    

    /** 返回平台 Locale 稳定只读列表。 */
    @GetMapping
    public Result<List<SupportedLocaleInfo>> supportedLocales() {
        return Result.success(application.supportedLocales());
    }

}
