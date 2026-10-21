package io.github.chrisshi.mom.mdm.controller;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.mdm.api.LocationReference;
import io.github.chrisshi.mom.mdm.application.LocationMasterDataApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通用厂内位置只读引用 HTTP 入口，仍由 MDM 持有静态主数据唯一权威。 */
@RestController
@RequestMapping("/location-references")
@RequiredArgsConstructor
public class LocationReferenceController {
    private final LocationMasterDataApplication application;

    

    /** 查询位置当前静态有效性，不推断 WMS 动态可用性。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mdm:location:read')")
    public Result<LocationReference> getReference(@PathVariable String id) {
        return Result.success(application.getLocationReference(id));
    }
}
