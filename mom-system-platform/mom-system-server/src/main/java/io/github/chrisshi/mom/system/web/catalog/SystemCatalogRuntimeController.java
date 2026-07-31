package io.github.chrisshi.mom.system.web.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.RuntimeCatalogView;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.RuntimeResult;
import io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

/** 已认证用户权限过滤后的不可执行 Catalog Runtime。 */
@RestController
@RequestMapping("/api/system/catalog")
@PreAuthorize("isAuthenticated()")
public class SystemCatalogRuntimeController {
    private final SystemCatalogApplicationService service;

    public SystemCatalogRuntimeController(SystemCatalogApplicationService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResponseEntity<RuntimeCatalogView> me(
            Authentication authentication,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return response(service.runtimeCatalog(authorities(authentication)), ifNoneMatch);
    }

    @GetMapping("/applications/{applicationCode}")
    public ResponseEntity<RuntimeCatalogView> application(
            @PathVariable String applicationCode,
            Authentication authentication,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return response(service.runtimeApplication(applicationCode, authorities(authentication)), ifNoneMatch);
    }

    private static Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ResponseEntity<RuntimeCatalogView> response(
            RuntimeResult result, String ifNoneMatch) {
        String etag = "\"" + result.checksum() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, etag)
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(result.view());
    }
}
