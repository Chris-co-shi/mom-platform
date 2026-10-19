package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.wms.application.InventoryTransferConflictException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/** 库位移动专有业务冲突的 HTTP 映射；权限仍由 Resource Server 检查。 */
@RestControllerAdvice(assignableTypes = InventoryController.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InventoryTransferExceptionHandler {
    private final I18nMessageResolver messages;

    /** @param messages WMS Owner 业务错误文案解析器 */
    public InventoryTransferExceptionHandler(I18nMessageResolver messages) { this.messages = messages; }

    /** 库存不足、SN 不可拆、状态变化及同键异内容返回稳定 409，不暴露 SQL。 */
    @ExceptionHandler(InventoryTransferConflictException.class)
    public ResponseEntity<Result<Void>> conflict(Locale locale) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Result.failure("wms.inventory.transfer_conflict",
                messages.resolve("wms.error.transfer_conflict", locale)));
    }
}
