package io.github.chrisshi.mom.security.web;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Servlet Security Filter Chain 的本地化 JSON 失败入口。
 *
 * <p>认证与授权异常发生在 Controller 之前，不能依赖 {@code RestControllerAdvice}。本类型同时实现
 * {@link AuthenticationEntryPoint} 与 {@link AccessDeniedHandler}，复用 Core 的消息解析契约但保持正确的
 * Security 入口。处理过程无共享可变状态且线程安全；写响应失败时向 Servlet 容器传播 IOException，不伪造
 * 成功。副作用仅为写入当前 HTTP 响应，不记录 Token 或异常详情。</p>
 */
public final class MomLocalizedSecurityErrorHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {
    private static final String AUTHENTICATION_KEY = "framework.security.unauthorized";
    private static final String ACCESS_DENIED_KEY = "framework.security.access_denied";

    private final I18nMessageResolver messageResolver;
    private final ObjectMapper objectMapper;

    /**
     * 创建统一 Security 失败入口。
     *
     * @param messageResolver 可为空的消息解析器；为空时直接返回稳定 messageKey
     * @param objectMapper Framework 管理的 Jackson JSON 序列化器
     */
    public MomLocalizedSecurityErrorHandler(
            I18nMessageResolver messageResolver,
            ObjectMapper objectMapper) {
        this.messageResolver = messageResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 写出未认证响应，不暴露认证异常内部细节。
     *
     * @param request 当前请求，Locale 仅用于展示
     * @param response 当前响应
     * @param exception Spring Security 认证异常，不进入响应正文
     * @throws IOException 响应流不可写时传播
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", AUTHENTICATION_KEY);
    }

    /**
     * 写出已认证但无权访问响应，不暴露授权规则内部细节。
     *
     * @param request 当前请求，Locale 仅用于展示
     * @param response 当前响应
     * @param exception Spring Security 授权异常，不进入响应正文
     * @throws IOException 响应流不可写时传播
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "ACCESS_DENIED", ACCESS_DENIED_KEY);
    }

    /** 统一设置 UTF-8 JSON 边界并序列化稳定 code 与本地化展示文本。 */
    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String messageKey) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String message = messageResolver == null
                ? messageKey : messageResolver.resolve(messageKey, request.getLocale());
        objectMapper.writeValue(response.getOutputStream(), new SecurityErrorResponse(code, message));
    }

    /** Security JSON 响应只暴露稳定机器码与可展示文案。 */
    private record SecurityErrorResponse(String code, String message) {
    }
}
