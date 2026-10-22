package io.github.chrisshi.mom.mdm.client;

import io.github.chrisshi.mom.mdm.api.*;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * MDM 主数据只读契约的同步内部 HTTP Client。
 *
 * <p>仅供具备当前用户 Bearer 委托的管理请求调用；后台作业没有服务身份时不能借此发起请求。
 * 调用方应在自身数据库事务外调用，并保留远端 401/403/404/409/503/504 失败语义。</p>
 */
@FeignClient(name = "mom-mdm-server", contextId = "mdmReferenceClient",
    configuration = MdmReferenceFeignConfiguration.class)
public interface MdmReferenceClient {
    /**
     * 查询物料当前引用有效性，不代表历史业务事实无效。
     */
    @GetMapping("/material-references/{id}")
    Result<MaterialReference> getMaterialReference(@PathVariable("id") String id);

    /**
     * 查询 Plant、Workshop、ProductionLine 或 Workstation 的完整父链有效性。
     */
    @GetMapping("/manufacturing-resource-references/{resourceType}/{id}")
    Result<ManufacturingResourceReference> getManufacturingResourceReference(
        @PathVariable("resourceType") String resourceType, @PathVariable("id") String id);

    /**
     * 查询单位当前有效性及量纲 ID，历史换算应另行固定规则版本。
     */
    @GetMapping("/uom-references/{id}")
    Result<UomReference> getUomReference(@PathVariable("id") String id);

    /**
     * 查询通用位置当前静态有效性；不可推断 WMS 动态占用。
     */
    @GetMapping("/location-references/{id}")
    Result<LocationReference> getLocationReference(@PathVariable("id") String id);

    /**
     * 查询仓库的静态新引用有效性。
     */
    @GetMapping("/warehouse-references/{id}")
    Result<WarehouseResourceReference> getWarehouseReference(@PathVariable("id") String id);

    /**
     * 查询库区及完整父链的静态新引用有效性。
     */
    @GetMapping("/warehouse-area-references/{id}")
    Result<WarehouseResourceReference> getWarehouseAreaReference(@PathVariable("id") String id);

    /**
     * 查询已发布制造定义组合当前是否可供新工单引用。
     */
    @GetMapping("/production-version-references/{id}")
    Result<ProductionVersionReference> getProductionVersionReference(@PathVariable("id") String id);

    /**
     * 查询质量标准当前已发布修订、检验用途及其新业务可用性。
     */
    @GetMapping("/quality-specification-references/{id}")
    Result<QualitySpecificationReference> getQualitySpecificationReference(@PathVariable("id") String id);
}
