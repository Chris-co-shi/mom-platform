package io.github.chrisshi.mom.iam.web.internal.permissionreference;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Permission Reference 内部 API 的受控错误映射。 */
@RestControllerAdvice(assignableTypes = IamPermissionReferenceController.class)
public class IamPermissionReferenceExceptionHandler {

    /** 非法输入和严格 JSON 反序列化失败统一返回 400，不回显原始 Payload。 */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(RuntimeException ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("invalid_request", "Permission 校验请求非法"));
    }

    /** 稳定错误响应，不暴露数据库、Token 或 Jackson 内部路径。 */
    public record ErrorResponse(String code, String message) {
    }
}
