package io.github.chrisshi.mom.iam.web;

import io.github.chrisshi.mom.iam.security.IamAccountAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.util.HtmlUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** IAM 自有最小登录页面与首次改密页面；不引入 BFF 或应用 API Session。 */
@Controller
public class IamAuthenticationPageController {
    private final IamAccountAuthenticationService accounts;
    private final SavedRequestAwareAuthenticationSuccessHandler continuation;

    public IamAuthenticationPageController(
            IamAccountAuthenticationService accounts,
            SavedRequestAwareAuthenticationSuccessHandler continuation) {
        this.accounts = accounts;
        this.continuation = continuation;
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public void loginPage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean failed = request.getParameter("error") != null;
        writeHtml(response, page("IAM 账号登录", """
                <h1>MOM IAM</h1>
                <p>使用统一账号登录。密码只提交到 IAM。</p>
                %s
                <form method="post" action="%s/login">
                  %s
                  <label>用户名<input name="username" autocomplete="username" required maxlength="120"></label>
                  <label>密码<input name="password" type="password" autocomplete="current-password" required maxlength="128"></label>
                  <button type="submit">登录</button>
                </form>
                """.formatted(
                failed ? "<p role=\"alert\">账号或密码错误，或账号当前不可用。</p>" : "",
                HtmlUtils.htmlEscape(request.getContextPath()),
                csrfField(request))));
    }

    @GetMapping(value = "/password/change", produces = MediaType.TEXT_HTML_VALUE)
    public void passwordChangePage(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (!accounts.requiresPasswordChange(authentication.getName())) {
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }
        boolean failed = request.getParameter("error") != null;
        writeHtml(response, page("首次修改密码", """
                <h1>首次修改密码</h1>
                <p>完成密码修改后将继续原授权请求。</p>
                %s
                <form method="post" action="%s/password/change">
                  %s
                  <label>新密码<input name="newPassword" type="password" autocomplete="new-password" required maxlength="128"></label>
                  <label>确认新密码<input name="confirmation" type="password" autocomplete="new-password" required maxlength="128"></label>
                  <button type="submit">修改并继续</button>
                </form>
                """.formatted(
                failed ? "<p role=\"alert\">密码不符合要求或两次输入不一致。</p>" : "",
                HtmlUtils.htmlEscape(request.getContextPath()),
                csrfField(request))));
    }

    @PostMapping("/password/change")
    public void changePassword(
            Authentication authentication,
            @RequestParam String newPassword,
            @RequestParam String confirmation,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            accounts.changeRequiredPassword(authentication.getName(), newPassword, confirmation);
            continuation.onAuthenticationSuccess(request, response, authentication);
        }
        catch (IllegalArgumentException | IllegalStateException exception) {
            response.sendRedirect(request.getContextPath() + "/password/change?error");
        }
    }

    private static String csrfField(HttpServletRequest request) {
        Object attribute = request.getAttribute("_csrf");
        if (!(attribute instanceof CsrfToken csrfToken)) {
            attribute = request.getAttribute(CsrfToken.class.getName());
        }
        if (!(attribute instanceof CsrfToken csrfToken)) {
            throw new IllegalStateException("IAM 表单缺少 CSRF Token");
        }
        return "<input type=\"hidden\" name=\"%s\" value=\"%s\">".formatted(
                HtmlUtils.htmlEscape(csrfToken.getParameterName()),
                HtmlUtils.htmlEscape(csrfToken.getToken()));
    }

    private static String page(String title, String body) {
        return """
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title>
                <style>body{font-family:sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem}
                form{display:grid;gap:1rem}label{display:grid;gap:.35rem}input,button{padding:.7rem}</style>
                </head><body>%s</body></html>
                """.formatted(HtmlUtils.htmlEscape(title), body);
    }

    private static void writeHtml(HttpServletResponse response, String html) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.getWriter().write(html);
    }
}
