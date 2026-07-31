package io.github.chrisshi.mom.system.web.preference;

import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 用户偏好 HTTP 的稳定脱敏错误映射。
 *
 * <p>400/401/409/413 使用稳定机器码；SQL、约束名、Stack Trace、Filter 原值和身份数据不会进入响应。
 * 未预期存储故障不在此吞掉，由平台 500/503 策略处理。</p>
 */
@RestControllerAdvice(assignableTypes = SystemUserPreferenceController.class)
public class SystemUserPreferenceExceptionHandler {
    /** 偏好格式、枚举、Code 或受限模型校验失败返回稳定 400。 */
    @ExceptionHandler(SystemUserPreferenceException.Invalid.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> validation(
            SystemUserPreferenceException.Invalid exception) {
        HttpStatus status = "payload_too_large".equals(exception.code())
                ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        return org.springframework.http.ResponseEntity.status(status)
                .body(new ErrorResponse(exception.code(), exception.getMessage()));
    }

    /** 缺少可信 JWT sub 时返回 401；正常情况下 Resource Server 会更早拒绝。 */
    @ExceptionHandler(SystemUserPreferenceException.NotAuthenticated.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse notAuthenticated(SystemUserPreferenceException.NotAuthenticated exception) {
        return new ErrorResponse(exception.code(), exception.getMessage());
    }

    /** 唯一创建竞争与 Version CAS 冲突统一返回 409 stale_version。 */
    @ExceptionHandler(SystemUserPreferenceException.StaleVersion.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse stale(RuntimeException exception) {
        return new ErrorResponse("stale_version", "偏好已被其他请求修改，请重新读取");
    }

    /** JSON 类型错误与普通参数错误返回稳定 400，不回显 Jackson 内部路径。 */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(Exception exception) {
        return new ErrorResponse("invalid_request", "请求参数格式非法");
    }

    /** 无成功信封且不包含内部身份或持久化细节的错误响应。 */
    public record ErrorResponse(String code, String message) {
    }
}
