package io.github.chrisshi.mom.system.web.catalog;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ApplicationPageQuery;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ApplicationStatusCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ApplicationView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.CatalogReleaseView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.CreateApplicationCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.CreateNavigationCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.NavigationStatusCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.NavigationTreeView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.NavigationView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.PageView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.PublishCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ReleaseHistoryView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ReorderItem;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ReorderNavigationCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.RollbackCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.UpdateApplicationCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.UpdateNavigationCommand;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationService;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogNavigationMoveApplicationService;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogPublishOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** Application Catalog 管理 HTTP 入站 Adapter。 */
@RestController
@RequestMapping("/api/system/admin/applications")
public class SystemCatalogAdminController {
    private final SystemCatalogApplicationService service;
    private final SystemCatalogNavigationMoveApplicationService moveService;
    private final SystemCatalogPublishOrchestrator publishOrchestrator;

    public SystemCatalogAdminController(
            SystemCatalogApplicationService service,
            SystemCatalogNavigationMoveApplicationService moveService,
            SystemCatalogPublishOrchestrator publishOrchestrator) {
        this.service = service;
        this.moveService = moveService;
        this.publishOrchestrator = publishOrchestrator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:catalog:write')")
    public ApplicationView create(@RequestBody CreateApplicationRequest request) {
        return service.createApplication(request.toCommand());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:catalog:write')")
    public ApplicationView update(@PathVariable String id, @RequestBody UpdateApplicationRequest request) {
        return service.updateApplication(id, request.toCommand());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:catalog:write')")
    public ApplicationView status(@PathVariable String id, @RequestBody StatusRequest request) {
        return service.changeApplicationStatus(id,
                new ApplicationStatusCommand(request.enabled(), request.version()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:catalog:read')")
    public ApplicationView get(@PathVariable String id) {
        return service.getApplication(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:catalog:read')")
    public PageView<ApplicationView> page(
            @RequestParam(required = false) String applicationCode,
            @RequestParam(required = false) ApplicationType applicationType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.pageApplications(new ApplicationPageQuery(
                applicationCode, applicationType, enabled, page, size));
    }

    @PostMapping("/{applicationId}/navigation")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:catalog:write')")
    public NavigationView createNavigation(
            @PathVariable String applicationId, @RequestBody CreateNavigationRequest request) {
        return service.createNavigation(applicationId, request.toCommand());
    }

    @PutMapping("/{applicationId}/navigation/{navigationId}")
    @PreAuthorize("hasAuthority('system:catalog:write')")
    public NavigationView updateNavigation(
            @PathVariable String applicationId, @PathVariable String navigationId,
            @RequestBody UpdateNavigationRequest request) {
        return service.updateNavigation(applicationId, navigationId, request.toCommand());
    }

    @PutMapping("/{applicationId}/navigation/{navigationId}/position")
    @PreAuthorize("hasAuthority('system:catalog:write')")
    public NavigationView moveNavigation(
            @PathVariable String applicationId, @PathVariable String navigationId,
            @RequestBody MoveNavigationRequest request) {
        return moveService.move(applicationId, navigationId, request.applicationVersion(),
                request.version(), request.parentId(), request.sortOrder());
    }

    @PatchMapping("/{applicationId}/navigation/{navigationId}/status")
    @PreAuthorize("hasAuthority('system:catalog:write')")
    public NavigationView navigationStatus(
            @PathVariable String applicationId, @PathVariable String navigationId,
            @RequestBody NavigationStatusRequest request) {
        return service.changeNavigationStatus(applicationId, navigationId,
                new NavigationStatusCommand(
                        request.applicationVersion(), request.version(), request.enabled()));
    }

    @PutMapping("/{applicationId}/navigation/order")
    @PreAuthorize("hasAuthority('system:catalog:write')")
    public NavigationTreeView reorder(
            @PathVariable String applicationId, @RequestBody ReorderNavigationRequest request) {
        return service.reorderNavigation(applicationId, request.toCommand());
    }

    @GetMapping("/{applicationId}/navigation/tree")
    @PreAuthorize("hasAuthority('system:catalog:read')")
    public NavigationTreeView tree(
            @PathVariable String applicationId, @RequestParam ClientChannel clientChannel) {
        return service.navigationTree(applicationId, clientChannel);
    }

    @PostMapping("/{applicationId}/catalog/publish")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:catalog:publish')")
    public CatalogReleaseView publish(
            @PathVariable String applicationId, @RequestBody PublishRequest request) {
        return publishOrchestrator.publish(applicationId,
                new PublishCommand(request.applicationVersion(), request.changeNote()));
    }

    @PostMapping("/{applicationId}/catalog/rollback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:catalog:publish')")
    public CatalogReleaseView rollback(
            @PathVariable String applicationId, @RequestBody RollbackRequest request) {
        return publishOrchestrator.rollback(applicationId, new RollbackCommand(
                request.targetReleaseVersion(), request.applicationVersion(), request.changeNote()));
    }

    @GetMapping("/{applicationId}/catalog/releases")
    @PreAuthorize("hasAuthority('system:catalog:read')")
    public PageView<ReleaseHistoryView> releases(
            @PathVariable String applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.releaseHistory(applicationId, page, size);
    }

    private static void rejectUnknown() {
        throw new IllegalArgumentException("Catalog 请求包含未声明字段");
    }

    public record CreateApplicationRequest(
            String applicationCode, ApplicationType applicationType,
            String i18nResourceCode, String i18nMessageKey, String iconKey, String description,
            Integer routeContractVersion, Integer sortOrder, Boolean enabled) {
        CreateApplicationCommand toCommand() {
            return new CreateApplicationCommand(applicationCode, applicationType, i18nResourceCode,
                    i18nMessageKey, iconKey, description, routeContractVersion, sortOrder, enabled);
        }
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record UpdateApplicationRequest(
            ApplicationType applicationType, String i18nResourceCode, String i18nMessageKey,
            String iconKey, String description, Integer routeContractVersion,
            Integer sortOrder, Long version) {
        UpdateApplicationCommand toCommand() {
            return new UpdateApplicationCommand(applicationType, i18nResourceCode, i18nMessageKey,
                    iconKey, description, routeContractVersion, sortOrder, version);
        }
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record StatusRequest(Boolean enabled, Long version) {
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record CreateNavigationRequest(
            Long applicationVersion, String parentId, ClientChannel clientChannel,
            NavigationType navigationType, String routeKey,
            String i18nResourceCode, String i18nMessageKey, String permissionCode, String iconKey,
            Boolean visibleInMenu, Boolean visibleInBreadcrumb, Boolean visibleInTab,
            Boolean keepAlive, Integer sortOrder, Boolean enabled) {
        CreateNavigationCommand toCommand() {
            return new CreateNavigationCommand(applicationVersion, parentId, clientChannel,
                    navigationType, routeKey, i18nResourceCode, i18nMessageKey, permissionCode,
                    iconKey, visibleInMenu, visibleInBreadcrumb, visibleInTab,
                    keepAlive, sortOrder, enabled);
        }
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record UpdateNavigationRequest(
            Long applicationVersion, Long version, String parentId,
            NavigationType navigationType, String i18nResourceCode, String i18nMessageKey,
            String permissionCode, String iconKey, Boolean visibleInMenu,
            Boolean visibleInBreadcrumb, Boolean visibleInTab, Boolean keepAlive, Integer sortOrder) {
        UpdateNavigationCommand toCommand() {
            return new UpdateNavigationCommand(applicationVersion, version, parentId, navigationType,
                    i18nResourceCode, i18nMessageKey, permissionCode, iconKey, visibleInMenu,
                    visibleInBreadcrumb, visibleInTab, keepAlive, sortOrder);
        }
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record MoveNavigationRequest(
            Long applicationVersion, Long version, String parentId, Integer sortOrder) {
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record NavigationStatusRequest(Long applicationVersion, Long version, Boolean enabled) {
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record ReorderNavigationRequest(
            Long applicationVersion, ClientChannel clientChannel, String parentId,
            List<ReorderItemRequest> items) {
        ReorderNavigationCommand toCommand() {
            return new ReorderNavigationCommand(applicationVersion, clientChannel, parentId,
                    items == null ? List.of() : items.stream().map(ReorderItemRequest::toCommand).toList());
        }
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record ReorderItemRequest(String navigationId, Long version, Integer sortOrder) {
        ReorderItem toCommand() {
            return new ReorderItem(navigationId, version, sortOrder);
        }
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record PublishRequest(Long applicationVersion, String changeNote) {
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }

    public record RollbackRequest(
            Long targetReleaseVersion, Long applicationVersion, String changeNote) {
        @JsonAnySetter private void unknown(String name, JsonNode value) { rejectUnknown(); }
    }
}
