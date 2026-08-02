package io.github.chrisshi.mom.iam.web.admin.client;

import io.github.chrisshi.mom.iam.application.admin.IamClientAdminApplicationService;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.web.admin.IamAdminWebSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** IAM OAuth Client Policy 管理 REST Adapter。 */
@RestController
@ConditionalOnBean(IamClientAdminApplicationService.class)
@RequestMapping("/api/iam/admin")
public class IamClientAdminController {
    private final IamClientAdminApplicationService clients;
    private final IamAdminWebSupport web;

    public IamClientAdminController(
            IamClientAdminApplicationService clients, IamAdminWebSupport web) {
        this.clients = clients;
        this.web = web;
    }

    @GetMapping("/oauth-clients")
    List<IamAdminViews.ClientView> clients(Authentication authentication) {
        return clients.listClients(web.actor(authentication));
    }

    @PutMapping("/oauth-clients/{clientId}/status")
    IamAdminViews.ClientView setClientStatus(
            Authentication authentication, HttpServletRequest request,
            @PathVariable String clientId,
            @RequestBody IamAdminCommands.ClientStatusChange command) {
        return clients.setClientStatus(
                web.actor(authentication), clientId, command, web.request(request));
    }
}
