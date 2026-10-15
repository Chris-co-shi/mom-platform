package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.api.ManufacturingResourceReference;
import io.github.chrisshi.mom.mdm.api.MaterialReference;
import io.github.chrisshi.mom.mdm.api.ProductionVersionReference;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.ProductionVersionPageParams;
import io.github.chrisshi.mom.mdm.application.model.ProductionVersionModels;
import io.github.chrisshi.mom.mdm.infrastructure.entity.FormulaEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.FormulaRevisionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.ProductionVersionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.RoutingEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.RoutingRevisionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.FormulaMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.FormulaRevisionMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.PlantMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.ProductionVersionMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.RoutingMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.RoutingRevisionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * ProductionVersion 草稿、发布和只读引用用例。
 *
 * <p>发布事务只写 MDM Schema，先锁定版本及其 Formula/Route 修订，再锁 Plant 和产出 Material。
 * Material 主行锁串行化同工厂同产出物料的发布，从而使有效期重叠检查不受幻读影响。</p>
 */
@Component
public class ProductionVersionApplication {
    private static final String DRAFT = "DRAFT";
    private static final String RELEASED = "RELEASED";
    private static final String OBSOLETE = "OBSOLETE";

    private final ProductionVersionMapper mapper;
    private final FormulaRevisionMapper formulaRevisionMapper;
    private final FormulaMapper formulaMapper;
    private final RoutingRevisionMapper routingRevisionMapper;
    private final RoutingMapper routingMapper;
    private final PlantMapper plantMapper;
    private final MaterialMapper materialMapper;
    private final FactoryStructureApplication factoryApplication;
    private final MaterialApplication materialApplication;
    private final PageAdapter pageAdapter;
    private final Clock clock;

    /** 注入 MDM 本地 Mapper、稳定引用用例、分页转换器和可测试时钟。 */
    public ProductionVersionApplication(ProductionVersionMapper mapper,
            FormulaRevisionMapper formulaRevisionMapper, FormulaMapper formulaMapper,
            RoutingRevisionMapper routingRevisionMapper, RoutingMapper routingMapper,
            PlantMapper plantMapper, MaterialMapper materialMapper,
            FactoryStructureApplication factoryApplication, MaterialApplication materialApplication,
            PageAdapter pageAdapter, Clock clock) {
        this.mapper = mapper;
        this.formulaRevisionMapper = formulaRevisionMapper;
        this.formulaMapper = formulaMapper;
        this.routingRevisionMapper = routingRevisionMapper;
        this.routingMapper = routingMapper;
        this.plantMapper = plantMapper;
        this.materialMapper = materialMapper;
        this.factoryApplication = factoryApplication;
        this.materialApplication = materialApplication;
        this.pageAdapter = pageAdapter;
        this.clock = clock;
    }

    /** 创建草稿；引用必须已经发布且工厂、产出物料一致。 */
    @Transactional
    public ProductionVersionModels.View create(String code, ProductionVersionModels.DraftInput input) {
        ProductionVersionEntity entity = new ProductionVersionEntity();
        entity.setCode(MdmMasterDataRules.code(code));
        applyDraft(entity, input);
        lockAndValidateDefinitions(entity);
        entity.setStatus(DRAFT);
        try { mapper.insert(entity); }
        catch (DuplicateKeyException exception) { throw MdmException.codeConflict("ProductionVersion"); }
        return view(entity);
    }

    /** 仅草稿允许修改业务版本、引用和有效期；Code 保持不可变。 */
    @Transactional
    public ProductionVersionModels.View updateDraft(String id, Long lockVersion,
                                                     ProductionVersionModels.DraftInput input) {
        ProductionVersionEntity entity = requireForUpdate(id);
        requireDraft(entity);
        requireVersion(entity.getLockVersion(), lockVersion);
        applyDraft(entity, input);
        lockAndValidateDefinitions(entity);
        requireUpdated(mapper.updateById(entity), entity.getId());
        return view(entity);
    }

    /** 发布前重新校验已发布定义和当前主数据，并拒绝半开区间重叠。 */
    @Transactional
    public ProductionVersionModels.View release(String id, Long lockVersion) {
        ProductionVersionEntity entity = requireForUpdate(id);
        requireDraft(entity);
        requireVersion(entity.getLockVersion(), lockVersion);
        lockAndValidateDefinitions(entity);
        List<ProductionVersionEntity> overlaps = mapper.selectReleasedOverlapsForUpdate(
                entity.getPlantId(), entity.getOutputMaterialId(), entity.getValidFrom(),
                entity.getValidTo(), entity.getId());
        if (!overlaps.isEmpty()) throw MdmException.productionVersionOverlap();
        entity.setStatus(RELEASED);
        entity.setReleasedAt(Instant.now(clock));
        requireUpdated(mapper.updateById(entity), entity.getId());
        return view(entity);
    }

    /** 废止已发布版本；历史引用仍可按稳定 ID 读取。 */
    @Transactional
    public ProductionVersionModels.View obsolete(String id, Long lockVersion) {
        ProductionVersionEntity entity = requireForUpdate(id);
        requireVersion(entity.getLockVersion(), lockVersion);
        if (!RELEASED.equals(entity.getStatus())) throw MdmException.immutable();
        entity.setStatus(OBSOLETE);
        entity.setObsoleteAt(Instant.now(clock));
        requireUpdated(mapper.updateById(entity), entity.getId());
        return view(entity);
    }

    /** 按稳定 ID 查询一个生产版本。 */
    @Transactional(readOnly = true)
    public ProductionVersionModels.View get(String id) { return view(require(id)); }

    /** 按工厂、产出物料和生命周期过滤并稳定分页。 */
    @Transactional(readOnly = true)
    public PageResult<ProductionVersionModels.View> search(PageQuery<ProductionVersionPageParams> query) {
        ProductionVersionPageParams params = query.params();
        Page<ProductionVersionEntity> page = pageAdapter.toPage(query);
        LambdaQueryWrapper<ProductionVersionEntity> conditions = new LambdaQueryWrapper<>();
        if (params.plantId() != null && !params.plantId().isBlank())
            conditions.eq(ProductionVersionEntity::getPlantId,
                    MdmMasterDataRules.id(params.plantId(), "plantId"));
        if (params.outputMaterialId() != null && !params.outputMaterialId().isBlank())
            conditions.eq(ProductionVersionEntity::getOutputMaterialId,
                    MdmMasterDataRules.id(params.outputMaterialId(), "outputMaterialId"));
        if (params.status() != null && !params.status().isBlank())
            conditions.eq(ProductionVersionEntity::getStatus, lifecycle(params.status()));
        conditions.orderByAsc(ProductionVersionEntity::getCode).orderByAsc(ProductionVersionEntity::getId);
        mapper.selectPage(page, conditions);
        return pageAdapter.toResult(page, ProductionVersionApplication::view);
    }

    /** 当前日期按工厂 IANA 时区判断新业务可用性，不否定历史工单事实。 */
    @Transactional(readOnly = true)
    public ProductionVersionReference getReference(String id) {
        ProductionVersionEntity entity = require(id);
        String reason = referenceInvalidReason(entity);
        return new ProductionVersionReference(entity.getId(), entity.getCode(), entity.getName(),
                entity.getBusinessVersion(), entity.getPlantId(), entity.getOutputMaterialId(),
                entity.getFormulaRevisionId(), entity.getRoutingRevisionId(), entity.getValidFrom(),
                entity.getValidTo(), entity.getLockVersion(), reason == null, reason);
    }

    private void applyDraft(ProductionVersionEntity entity, ProductionVersionModels.DraftInput input) {
        if (input == null) throw MdmException.invalidReference();
        entity.setName(MdmMasterDataRules.name(input.name()));
        entity.setBusinessVersion(MdmMasterDataRules.code(input.businessVersion()));
        entity.setPlantId(MdmMasterDataRules.id(input.plantId(), "plantId"));
        entity.setOutputMaterialId(MdmMasterDataRules.id(input.outputMaterialId(), "outputMaterialId"));
        entity.setFormulaRevisionId(MdmMasterDataRules.id(input.formulaRevisionId(), "formulaRevisionId"));
        entity.setRoutingRevisionId(MdmMasterDataRules.id(input.routingRevisionId(), "routingRevisionId"));
        if (input.validFrom() == null || input.validTo() != null && !input.validTo().isAfter(input.validFrom()))
            throw MdmException.invalidReference();
        entity.setValidFrom(input.validFrom());
        entity.setValidTo(input.validTo());
    }

    private void lockAndValidateDefinitions(ProductionVersionEntity entity) {
        FormulaRevisionEntity formulaRevision = formulaRevisionMapper.selectByIdForUpdate(entity.getFormulaRevisionId());
        RoutingRevisionEntity routingRevision = routingRevisionMapper.selectByIdForUpdate(entity.getRoutingRevisionId());
        if (formulaRevision == null || routingRevision == null
                || !RELEASED.equals(formulaRevision.getStatus()) || !RELEASED.equals(routingRevision.getStatus()))
            throw MdmException.invalidReference();
        FormulaEntity formula = formulaMapper.selectByIdForUpdate(formulaRevision.getFormulaId());
        RoutingEntity routing = routingMapper.selectByIdForUpdate(routingRevision.getRoutingId());
        if (formula == null || routing == null
                || !entity.getPlantId().equals(formula.getPlantId())
                || !entity.getPlantId().equals(routing.getPlantId())
                || !entity.getOutputMaterialId().equals(formula.getOutputMaterialId()))
            throw MdmException.invalidReference();
        if (plantMapper.selectByIdForUpdate(entity.getPlantId()) == null
                || materialMapper.selectByIdForUpdate(entity.getOutputMaterialId()) == null)
            throw MdmException.invalidReference();
        ManufacturingResourceReference plant = factoryApplication.getManufacturingResourceReference(
                "PLANT", entity.getPlantId());
        MaterialReference material = materialApplication.getMaterialReference(entity.getOutputMaterialId());
        if (!plant.availableForNewUse() || !material.availableForNewUse()) throw MdmException.invalidReference();
    }

    private String referenceInvalidReason(ProductionVersionEntity entity) {
        if (DRAFT.equals(entity.getStatus())) return "PRODUCTION_VERSION_DRAFT";
        if (OBSOLETE.equals(entity.getStatus())) return "PRODUCTION_VERSION_OBSOLETE";
        FormulaRevisionEntity formula = formulaRevisionMapper.selectById(entity.getFormulaRevisionId());
        RoutingRevisionEntity routing = routingRevisionMapper.selectById(entity.getRoutingRevisionId());
        if (formula == null || routing == null || !RELEASED.equals(formula.getStatus())
                || !RELEASED.equals(routing.getStatus())) return "DEFINITION_NOT_RELEASED";
        ManufacturingResourceReference plant = factoryApplication.getManufacturingResourceReference(
                "PLANT", entity.getPlantId());
        MaterialReference material = materialApplication.getMaterialReference(entity.getOutputMaterialId());
        if (!plant.availableForNewUse() || !material.availableForNewUse()) return "REFERENCE_UNAVAILABLE";
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(plant.timeZoneId())));
        if (today.isBefore(entity.getValidFrom())) return "NOT_YET_VALID";
        if (entity.getValidTo() != null && !today.isBefore(entity.getValidTo())) return "EXPIRED";
        return null;
    }

    private ProductionVersionEntity require(String id) {
        ProductionVersionEntity entity = mapper.selectById(MdmMasterDataRules.id(id, "productionVersionId"));
        if (entity == null) throw MdmException.notFound("ProductionVersion");
        return entity;
    }

    private ProductionVersionEntity requireForUpdate(String id) {
        ProductionVersionEntity entity = mapper.selectByIdForUpdate(
                MdmMasterDataRules.id(id, "productionVersionId"));
        if (entity == null) throw MdmException.notFound("ProductionVersion");
        return entity;
    }

    private static void requireDraft(ProductionVersionEntity entity) {
        if (!DRAFT.equals(entity.getStatus())) throw MdmException.immutable();
    }

    private void requireUpdated(int affected, String id) {
        if (affected == 1) return;
        if (mapper.selectById(id) == null) throw MdmException.notFound("ProductionVersion");
        throw MdmException.versionConflict();
    }

    private static String lifecycle(String value) {
        if (!DRAFT.equals(value) && !RELEASED.equals(value) && !OBSOLETE.equals(value))
            throw MdmException.invalidReference();
        return value;
    }

    private static ProductionVersionModels.View view(ProductionVersionEntity entity) {
        return new ProductionVersionModels.View(entity.getId(), entity.getCode(), entity.getName(),
                entity.getBusinessVersion(), entity.getPlantId(), entity.getOutputMaterialId(),
                entity.getFormulaRevisionId(), entity.getRoutingRevisionId(), entity.getValidFrom(),
                entity.getValidTo(), entity.getStatus(), entity.getLockVersion(), entity.getReleasedAt(),
                entity.getObsoleteAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
