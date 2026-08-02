package io.github.chrisshi.mom.iam.web.admin.audit;

import io.github.chrisshi.mom.iam.application.admin.IamSecurityAuditQueryService;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.web.admin.IamAdminWebSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** IAM 安全审计只读 REST Adapter。 */
@RestController
@ConditionalOnBean(IamSecurityAuditQueryService.class)
@RequestMapping("/api/iam/admin")
public class IamSecurityAuditController {
    private final IamSecurityAuditQueryService audits;
    private final IamAdminWebSupport web;

    public IamSecurityAuditController(
            IamSecurityAuditQueryService audits, IamAdminWebSupport web) {
        this.audits = audits;
        this.web = web;
    }

    @GetMapping("/security-audit")
    List<IamAdminViews.SecurityAuditView> securityAudit(
            Authentication authentication,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return audits.listAudit(
                web.actor(authentication), category, targetId, limit, offset);
    }
}
