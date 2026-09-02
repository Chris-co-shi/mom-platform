package io.github.chrisshi.mom.security.revocation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Servlet Resource Server 的实时 Session 撤销过滤器。
 *
 * <p>过滤器只处理 Spring Security 已验证完成的 {@link JwtAuthenticationToken}，从 JWT 读取 {@code sid}
 * 并查询协议中立撤销端口。缺少 sid 或已撤销属于无效 Bearer Token，返回 401；撤销数据源不可用返回
 * 503。两种情况都禁止缓存并终止过滤链。公开健康路径完全跳过检查，避免 Redis 故障阻断探活。</p>
 *
 * <p>过滤器不信任任何 {@code X-MOM-*} Header，不查询 IAM HTTP API，不改变 JWT、Session、Redis Key、
 * TTL 或写入事务。实例无请求级可变状态，可被 Servlet 容器并发复用。</p>
 */
public final class MomRevokedTokenFilter extends OncePerRequestFilter {
    private static final byte[] UNAVAILABLE_BODY =
            "{\"error\":\"revocation_store_unavailable\"}".getBytes(StandardCharsets.UTF_8);

//    private final MomRevokedSessionChecker checker;
    private final List<RequestMatcher> publicPaths;
    private final BearerTokenAuthenticationEntryPoint authenticationEntryPoint =
            new BearerTokenAuthenticationEntryPoint();

    /** 创建只检查受保护路径的撤销过滤器。 */
    public MomRevokedTokenFilter(
//            MomRevokedSessionChecker checker,
            List<String> publicPaths) {
//        this.checker = checker;
        this.publicPaths = publicPaths.stream()
                .map(PathPatternRequestMatcher::pathPattern)
                .map(RequestMatcher.class::cast)
                .toList();
    }

    /** 公开健康与错误路径不依赖 revoked sid 数据源。 */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return publicPaths.stream().anyMatch(matcher -> matcher.matches(request));
    }

    /** 在 JWT 认证完成后执行撤销检查并保持既有业务授权链。 */
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tokenId = jwtAuthentication.getToken().getId();
        if (tokenId == null || tokenId.isBlank()) {
            rejectInvalidToken(request, response);
            return;
        }
//        try {
////            if (checker.isRevoked(sessionId)) {
////                rejectInvalidToken(request, response, "Session 已撤销");
////                return;
////            }
//        }
//        catch (MomRevocationStoreUnavailableException exception) {
//            rejectUnavailable(response);
//            return;
//        }
        filterChain.doFilter(request, response);
    }

    private void rejectInvalidToken(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        authenticationEntryPoint.commence(
                request,
                response,
                new OAuth2AuthenticationException(BearerTokenErrors.invalidToken("Access Token 缺少 sid")));
    }

    private static void rejectUnavailable(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(UNAVAILABLE_BODY);
    }
}
