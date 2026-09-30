package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.mdm.application.UomConversionApplication;
import io.github.chrisshi.mom.mdm.application.UomMasterDataViews.CompatibilityView;
import io.github.chrisshi.mom.mdm.application.UomMasterDataViews.ConversionView;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 计量单位换算和兼容性识别的 HTTP 边界。
 *
 * <p>数值以十进制字符串传输，避免 JSON Number 浮点损失；该类只适配协议并包装 Result，算法、事务和
 * fail-closed 行为均在 Application。所有端点只读但仍要求明确的 {@code mdm:uom:read} 权限。</p>
 */
@RestController
@RequestMapping("/api/mdm/uom-conversions")
@PreAuthorize("hasAuthority('mdm:uom:read')")
public class UomConversionController {
    private final UomConversionApplication application;

    /**
     * 注入换算用例入口。
     * @param application 不感知 HTTP Result 的换算 Application
     */
    public UomConversionController(UomConversionApplication application) { this.application = application; }

    /**
     * 使用当前启用规则换算；请求幂等且无持久化副作用。
     * @param r 已完成基础格式校验的换算请求
     * @return 包含规则身份的统一换算结果
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 单位、状态、兼容性或规则校验失败时抛出
     */
    @PostMapping("/convert")
    public Result<ConversionView> convert(@Valid @RequestBody ConvertRequest r) { return Result.success(application.convertCurrent(r.value(), r.sourceUomId(), r.targetUomId())); }

    /**
     * 使用明确规则版本重放历史换算；不会读取当前规则替代指定版本。
     * @param r 指定两侧规则版本的重放请求
     * @return 可审计的统一换算结果；只读且幂等
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 单位、兼容性或历史版本校验失败时抛出
     */
    @PostMapping("/replay")
    public Result<ConversionView> replay(@Valid @RequestBody ReplayRequest r) { return Result.success(application.replay(r.value(), r.sourceUomId(), r.targetUomId(), r.sourceRuleVersion(), r.targetRuleVersion())); }

    /**
     * 判断两个现有单位能否直接换算；跨类别返回 false。
     * @param sourceUomId 源单位技术主键
     * @param targetUomId 目标单位技术主键
     * @return 统一兼容性结果；只读、幂等且无副作用
     * @throws io.github.chrisshi.mom.mdm.application.MdmException 任一单位不存在时抛出
     */
    @GetMapping("/compatibility")
    public Result<CompatibilityView> compatibility(
            @RequestParam @NotBlank @Size(max = 19) String sourceUomId,
            @RequestParam @NotBlank @Size(max = 19) String targetUomId) {
        return Result.success(application.compatibility(sourceUomId, targetUomId));
    }

    /**
     * 当前规则换算协议；value 必须是十进制字符串。
     *
     * @param value 原始十进制字符串
     * @param sourceUomId 源单位技术主键
     * @param targetUomId 目标单位技术主键
     */
    public record ConvertRequest(@NotBlank @Size(max=200) String value,
                                 @NotBlank @Size(max=19) String sourceUomId,
                                 @NotBlank @Size(max=19) String targetUomId) { }

    /**
     * 历史重放协议；非基准单位一侧必须提供正版本，基准单位一侧必须留空。
     *
     * @param value 原始十进制字符串
     * @param sourceUomId 源单位技术主键
     * @param targetUomId 目标单位技术主键
     * @param sourceRuleVersion 可选源规则版本
     * @param targetRuleVersion 可选目标规则版本
     */
    public record ReplayRequest(@NotBlank @Size(max=200) String value,
                                @NotBlank @Size(max=19) String sourceUomId,
                                @NotBlank @Size(max=19) String targetUomId, @Positive Integer sourceRuleVersion,
                                @Positive Integer targetRuleVersion) { }
}
