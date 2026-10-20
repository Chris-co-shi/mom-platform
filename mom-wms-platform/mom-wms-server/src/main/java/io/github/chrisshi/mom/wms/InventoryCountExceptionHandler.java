package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.wms.application.InventoryCountLimitException;
import io.github.chrisshi.mom.wms.application.InventoryCountFrozenDifferenceException;
import io.github.chrisshi.mom.wms.application.OpeningInventoryReferenceException;
import io.github.chrisshi.mom.wms.controller.InventoryCountController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Locale;

/** 库存盘点 HTTP 业务错误边界；不向客户端泄露 SQL 或数据库约束。 */
@RestControllerAdvice(assignableTypes=InventoryCountController.class)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class InventoryCountExceptionHandler {
    private final I18nMessageResolver messages;
    /** @param messages WMS Owner 国际化错误文案 */
    public InventoryCountExceptionHandler(I18nMessageResolver messages) { this.messages=messages; }
    /** 非零差异超出整单记账上限，提示缩小盘点范围。 */
    @ExceptionHandler(InventoryCountLimitException.class)
    public ResponseEntity<Result<Void>> limit(Locale locale) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Result.failure("wms.inventory.count_limit",
            messages.resolve("wms.error.count_limit",locale)));
    }
    /** 冻结余额差异需人工核对限制后重盘。 */
    @ExceptionHandler(InventoryCountFrozenDifferenceException.class)
    public ResponseEntity<Result<Void>> frozenDifference(Locale locale) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Result.failure("wms.inventory.count_frozen_difference",
            messages.resolve("wms.error.count_frozen_difference",locale)));
    }
    /** 请求参数、数量或 JSON 非法。 */
    @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class,OpeningInventoryReferenceException.class})
    public ResponseEntity<Result<Void>> invalid(Locale locale) {
        return ResponseEntity.badRequest().body(Result.failure("request.invalid_body",
            messages.resolve("wms.error.count_invalid",locale)));
    }
    /** 状态、整仓锁或唯一约束冲突。 */
    @ExceptionHandler({IllegalStateException.class,DataIntegrityViolationException.class})
    public ResponseEntity<Result<Void>> conflict(Locale locale) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.failure("wms.inventory.count_conflict",
            messages.resolve("wms.error.count_conflict",locale)));
    }
    /** 数据库不可用时明确失败，不伪造成功。 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> unavailable(Locale locale) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Result.failure("wms.inventory_unavailable",
            messages.resolve("wms.error.inventory_unavailable",locale)));
    }
}
