package io.github.chrisshi.mom.gateway.error;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gateway 可预期异常的统一出口。
 *
 * <p>Filter 与限流组件只抛 {@link GatewayException}，不自行拼 JSON。这里统一完成状态码、稳定错误结构和
 * JSON 序列化；V1 直接使用 {@link GatewayErrorCode#defaultMessage()}。国际化只通过 ErrorCode 的
 * messageKey 预留扩展点，当前不解析 Locale、不加载消息资源。</p>
 *
 * <p>未识别异常继续交给 Spring Cloud Gateway 默认错误处理链。</p>
 */
@Component
public final class GatewayExceptionHandler implements WebExceptionHandler, Ordered {

    private final JsonMapper jsonMapper;

    public GatewayExceptionHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        GatewayException gatewayException = findGatewayException(exception);
        if (gatewayException == null || exchange.getResponse().isCommitted()) {
            return Mono.error(exception);
        }

        GatewayErrorCode error = gatewayException.errorCode();
        GatewayErrorResponse response = new GatewayErrorResponse(
                error.code(),
                error.defaultMessage());
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
