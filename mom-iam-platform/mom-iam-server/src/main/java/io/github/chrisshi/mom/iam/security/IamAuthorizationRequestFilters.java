package io.github.chrisshi.mom.iam.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 授权请求只允许带 S256 Challenge 的 Authorization Code 流程。 */
final class PkceS256AuthorizationRequestFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isAuthorizationEndpoint(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if ("code".equals(request.getParameter("response_type"))) {
            String challenge = request.getParameter("code_challenge");
            String method = request.getParameter("code_challenge_method");
            if (challenge == null || challenge.isBlank() || !"S256".equals(method)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid authorization request");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isAuthorizationEndpoint(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String expected = (contextPath == null ? "" : contextPath) + "/oauth2/authorize";
        return expected.equals(requestUri);
    }
}

/** 已登录用户在进入官方 Authorization Endpoint 前执行 MOM Client 访问矩阵校验。 */
final class IamClientAuthorizationRequestFilter extends OncePerRequestFilter {
    private final IamAccountAuthenticationService accounts;
    private final IamClientAccessPolicyService accessPolicy;
    private final RequestCache requestCache;

    IamClientAuthorizationRequestFilter(
            IamAccountAuthenticationService accounts,
            IamClientAccessPolicyService accessPolicy,
            RequestCache requestCache) {
        this.accounts = accounts;
        this.accessPolicy = accessPolicy;
        this.requestCache = requestCache;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String expected = (contextPath == null ? "" : contextPath) + "/oauth2/authorize";
        return !expected.equals(requestUri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }
        if (accounts.requiresPasswordChange(authentication.getName())) {
            requestCache.saveRequest(request, response);
            response.sendRedirect(request.getContextPath() + "/password/change");
            return;
        }
        try {
            accessPolicy.requireAuthorization(authentication.getName(), request.getParameter("client_id"));
        }
        catch (IamClientAccessPolicyService.AccessDeniedException exception) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "authorization denied");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
