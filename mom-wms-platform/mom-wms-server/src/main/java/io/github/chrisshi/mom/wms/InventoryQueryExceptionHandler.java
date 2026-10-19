package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.core.page.PageQueryValidationException;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/** 库存只读 HTTP 输入和数据库失败适配，不把 SQL 或异常明细暴露给浏览器。 */
@RestControllerAdvice(assignableTypes = InventoryController.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InventoryQueryExceptionHandler {
    private final I18nMessageResolver messages;

    /** @param messages 框架固定错误文案解析器 */
    public InventoryQueryExceptionHandler(I18nMessageResolver messages) { this.messages = messages; }

    /** 非法筛选、分页与 JSON 均返回稳定 400。 */
    @ExceptionHandler({IllegalArgumentException.class, PageQueryValidationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> invalid(Locale locale) {
        return ResponseEntity.badRequest().body(Result.failure("request.invalid_body",
                messages.resolve("framework.web.invalid_request", locale)));
    }

    /** 数据库不可用不返回空库存或伪造成功。 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> unavailable(Locale locale) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.failure("wms.inventory_unavailable",
                messages.resolve("wms.error.inventory_unavailable", locale)));
    }
}
