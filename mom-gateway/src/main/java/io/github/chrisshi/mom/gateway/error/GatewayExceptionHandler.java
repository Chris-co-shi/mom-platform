package io.github.chrisshi.mom.gateway.error;

import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;

/**
 * Gateway 可预期异常的统一出口。
 *
 * <p>Filter 与限流组件只抛 {@link GatewayException}，不自行拼 JSON。这里统一完成状态码、稳定错误结构、
 * MessageSource 国际化解析和 JSON 序列化；未识别异常继续交给 Spring Cloud Gateway 默认错误处理链。</p>
 */
@Component
public final class GatewayExceptionHandler implements WebExceptionHandler, Ordered {

    private final JsonMapper jsonMapper;
    private final MessageSource messageSource;

    public GatewayExceptionHandler(JsonMapper jsonMapper, MessageSource messageSource) {
        this.jsonMapper = jsonMapper;
        this.messageSource = messageSource;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        GatewayException gatewayException = findGatewayException(exception);
        if (gatewayException == null || exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        GatewayErrorCode error = gatewayException.errorCode();
        Locale locale = exchange.getLocaleContext().getLocale();
        String message = messageSource.getMessage(
                error.messageKey(),
                null,
                error.defaultMessage(),
                locale);
        GatewayErrorResponse response = new GatewayErrorResponse(error.code(), message);
        byte[] body = jsonMapper.writeValueAsBytes(response);

        exchange.getResponse().setStatusCode(error.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static GatewayException findGatewayException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof GatewayException gatewayException) {
                return gatewayException;
            }
            current = current.getCause();
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
