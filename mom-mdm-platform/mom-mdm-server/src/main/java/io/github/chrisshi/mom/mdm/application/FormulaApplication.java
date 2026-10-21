package io.github.chrisshi.mom.mdm.application;

import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.api.ManufacturingResourceReference;
import io.github.chrisshi.mom.mdm.api.MaterialReference;
import io.github.chrisshi.mom.mdm.api.UomReference;
import io.github.chrisshi.mom.mdm.application.model.FormulaModels;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.FormulaPageParams;
import io.github.chrisshi.mom.mdm.infrastructure.entity.FormulaEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.FormulaItemEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.FormulaRevisionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.FormulaItemMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.FormulaMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.FormulaRevisionMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.MaterialMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.PlantMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.UomMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * 配方主数据与修订的 MDM 本地事务用例；不生成 MES 执行指令或工单快照。
 *
 * <p>创建和草稿修改校验 Plant、Material、UOM 的当前有效性；发布时重新校验并按
 * Plant → Material ID → UOM ID 的稳定顺序加行锁。已发布明细不可变，修正只能创建下一修订。
 * 配方写入只落 MDM Schema，任何失败整体回滚。</p>
 */
@Component
@RequiredArgsConstructor
public class FormulaApplication {
    private static final String DRAFT = "DRAFT";
    private static final String RELEASED = "RELEASED";
    private static final String OBSOLETE = "OBSOLETE";

    private final FormulaMapper formulaMapper;
    private final FormulaRevisionMapper revisionMapper;
    private final FormulaItemMapper itemMapper;
    private final PlantMapper plantMapper;
    private final MaterialMapper materialMapper;
    private final UomMapper uomMapper;
    private final FactoryStructureApplication factoryApplication;
    private final MaterialApplication materialApplication;
    private final UomMasterDataApplication uomApplication;
    private final PageAdapter pageAdapter;

    

    /** 原子创建配方身份、第一草稿修订和有序明细；重复 Code 不是幂等成功。 */
    @Transactional
    public FormulaModels.DetailView createFormula(
            String code, String name, String plantId, String outputMaterialId,
            String baseBatchQuantity, String baseUomId, List<FormulaModels.ItemInput> items) {
        FormulaEntity formula = new FormulaEntity();
        formula.setCode(MdmMasterDataRules.code(code));
        formula.setName(MdmMasterDataRules.name(name));
        formula.setPlantId(MdmMasterDataRules.id(plantId, "plantId"));
        formula.setOutputMaterialId(MdmMasterDataRules.id(outputMaterialId, "outputMaterialId"));
        formula.setBaseBatchQuantity(quantity(baseBatchQuantity));
        formula.setBaseUomId(MdmMasterDataRules.id(baseUomId, "baseUomId"));
        List<FormulaModels.ItemInput> validatedItems = validateItems(items);
        lockAndValidateReferences(formula, validatedItems);

        try {
            formulaMapper.insert(formula);
        } catch (DuplicateKeyException exception) {
            throw MdmException.codeConflict("Formula");
        }
        FormulaRevisionEntity revision = newRevision(formula.getId(), 1);
        revisionMapper.insert(revision);
        insertItems(revision.getId(), validatedItems);
        return detail(formula, revision);
    }

    /** 仅 DRAFT 修订可在技术锁版本匹配时整体替换有序明细。 */
    @Transactional
    public FormulaModels.DetailView updateDraft(
            String revisionId, Long lockVersion, List<FormulaModels.ItemInput> items) {
        FormulaRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireDraft(revision);
        requireVersion(revision.getLockVersion(), lockVersion);
        FormulaEntity formula = requireFormula(revision.getFormulaId());
        List<FormulaModels.ItemInput> validatedItems = validateItems(items);
        lockAndValidateReferences(formula, validatedItems);
        itemMapper.delete(new LambdaQueryWrapper<FormulaItemEntity>()
                .eq(FormulaItemEntity::getRevisionId, revision.getId()));
        insertItems(revision.getId(), validatedItems);
        requireUpdated(revisionMapper.updateById(revision), revision.getId());
        return detail(formula, revision);
    }

    /** 从上一已发布或已废止修订克隆新草稿，同一 Formula 下修订号由主行锁串行分配。 */
    @Transactional
    public FormulaModels.DetailView createNextRevision(String formulaId) {
        String id = MdmMasterDataRules.id(formulaId, "formulaId");
        FormulaEntity formula = formulaMapper.selectByIdForUpdate(id);
        if (formula == null) throw MdmException.notFound("Formula");
        FormulaRevisionEntity latest = latestRevision(id);
        if (latest == null) throw MdmException.invalidReference();
        if (DRAFT.equals(latest.getStatus())) throw MdmException.immutable();
        List<FormulaModels.ItemInput> items = loadItems(latest.getId()).stream()
                .map(item -> new FormulaModels.ItemInput(item.getSequenceNo(), item.getMaterialId(),
                        item.getQuantity().toPlainString(), item.getUomId()))
                .toList();
        lockAndValidateReferences(formula, items);
        FormulaRevisionEntity next = newRevision(id, latest.getRevisionNo() + 1);
        revisionMapper.insert(next);
        insertItems(next.getId(), items);
        return detail(formula, next);
    }

    /** 发布 DRAFT，重新校验所有当前引用和量纲；发布后内容永久不可修改。 */
    @Transactional
    public FormulaModels.DetailView releaseRevision(String revisionId, Long lockVersion) {
        FormulaRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireDraft(revision);
        requireVersion(revision.getLockVersion(), lockVersion);
        FormulaEntity formula = requireFormula(revision.getFormulaId());
        List<FormulaModels.ItemInput> items = loadItems(revision.getId()).stream()
                .map(item -> new FormulaModels.ItemInput(item.getSequenceNo(), item.getMaterialId(),
                        item.getQuantity().toPlainString(), item.getUomId()))
                .toList();
        if (items.isEmpty()) throw MdmException.invalidReference();
        lockAndValidateReferences(formula, items);
        revision.setStatus(RELEASED);
        revision.setReleasedAt(Instant.now());
        requireUpdated(revisionMapper.updateById(revision), revision.getId());
        return detail(formula, revision);
    }

    /** 仅已发布修订可废止；历史定义仍可按 ID 查询，不能退回草稿。 */
    @Transactional
    public FormulaModels.DetailView obsoleteRevision(String revisionId, Long lockVersion) {
        FormulaRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireVersion(revision.getLockVersion(), lockVersion);
        if (!RELEASED.equals(revision.getStatus())) throw MdmException.immutable();
        revision.setStatus(OBSOLETE);
        revision.setObsoleteAt(Instant.now());
        requireUpdated(revisionMapper.updateById(revision), revision.getId());
        return detail(requireFormula(revision.getFormulaId()), revision);
    }

    /** 返回 Formula 当前最新修订及明细；历史修订可按 revision ID 单独读取。 */
    @Transactional(readOnly = true)
    public FormulaModels.DetailView getFormula(String id) {
        FormulaEntity formula = requireFormula(id);
        FormulaRevisionEntity revision = latestRevision(formula.getId());
        if (revision == null) throw MdmException.invalidReference();
        return detail(formula, revision);
    }

    /** 按稳定修订 ID 读取历史定义，不会把废止修订伪装成当前可新用版本。 */
    @Transactional(readOnly = true)
    public FormulaModels.DetailView getRevision(String id) {
        FormulaRevisionEntity revision = requireRevision(id);
        return detail(requireFormula(revision.getFormulaId()), revision);
    }

    /** 按工厂、产出物料组合过滤并按 Code/ID 稳定分页。 */
    @Transactional(readOnly = true)
    public PageResult<FormulaModels.FormulaView> pageFormulas(PageQuery<FormulaPageParams> query) {
        FormulaPageParams params = query.params();
        Page<FormulaEntity> page = pageAdapter.toPage(query);
        LambdaQueryWrapper<FormulaEntity> conditions = new LambdaQueryWrapper<>();
        if (params.plantId() != null && !params.plantId().isBlank()) {
            conditions.eq(FormulaEntity::getPlantId, MdmMasterDataRules.id(params.plantId(), "plantId"));
        }
        if (params.outputMaterialId() != null && !params.outputMaterialId().isBlank()) {
            conditions.eq(FormulaEntity::getOutputMaterialId,
                    MdmMasterDataRules.id(params.outputMaterialId(), "outputMaterialId"));
        }
        conditions.orderByAsc(FormulaEntity::getCode).orderByAsc(FormulaEntity::getId);
        formulaMapper.selectPage(page, conditions);
        return pageAdapter.toResult(page, FormulaApplication::formulaView);
    }

    private void lockAndValidateReferences(FormulaEntity formula, List<FormulaModels.ItemInput> items) {
        if (plantMapper.selectByIdForUpdate(formula.getPlantId()) == null) throw MdmException.invalidReference();
        ManufacturingResourceReference plant = factoryApplication.getManufacturingResourceReference(
                "PLANT", formula.getPlantId());
        if (!plant.availableForNewUse()) throw MdmException.invalidReference();

        Set<String> materialIds = new TreeSet<>();
        materialIds.add(formula.getOutputMaterialId());
        for (FormulaModels.ItemInput item : items) materialIds.add(item.materialId());
        Map<String, MaterialReference> materials = new HashMap<>();
        for (String id : materialIds) {
            if (materialMapper.selectByIdForUpdate(id) == null) throw MdmException.invalidReference();
            MaterialReference material = materialApplication.getMaterialReference(id);
            if (!material.availableForNewUse()) throw MdmException.invalidReference();
            materials.put(id, material);
        }

        Set<String> uomIds = new TreeSet<>();
        uomIds.add(formula.getBaseUomId());
        for (MaterialReference material : materials.values()) uomIds.add(material.baseUomId());
        for (FormulaModels.ItemInput item : items) uomIds.add(item.uomId());
        Map<String, UomReference> uoms = new HashMap<>();
        for (String id : uomIds) {
            if (uomMapper.selectByIdForUpdate(id) == null) throw MdmException.invalidReference();
            UomReference uom = uomApplication.getUomReference(id);
            if (!uom.availableForNewUse()) throw MdmException.invalidReference();
            uoms.put(id, uom);
        }
        if (!sameDimension(uoms, formula.getBaseUomId(),
                materials.get(formula.getOutputMaterialId()).baseUomId())) throw MdmException.incompatibleUom();
        for (FormulaModels.ItemInput item : items) {
            if (!sameDimension(uoms, item.uomId(), materials.get(item.materialId()).baseUomId())) {
                throw MdmException.incompatibleUom();
            }
        }
    }

    private static boolean sameDimension(Map<String, UomReference> uoms, String firstId, String secondId) {
        return uoms.get(firstId).dimensionId().equals(uoms.get(secondId).dimensionId());
    }

    private static List<FormulaModels.ItemInput> validateItems(List<FormulaModels.ItemInput> items) {
        if (items == null || items.isEmpty() || items.size() > 500) throw MdmException.invalidReference();
        Set<Integer> sequences = new HashSet<>();
        List<FormulaModels.ItemInput> validated = new ArrayList<>();
        for (FormulaModels.ItemInput item : items) {
            if (item == null || item.sequenceNo() == null || item.sequenceNo() <= 0
                    || !sequences.add(item.sequenceNo())) throw MdmException.invalidReference();
            validated.add(new FormulaModels.ItemInput(item.sequenceNo(),
                    MdmMasterDataRules.id(item.materialId(), "materialId"),
                    quantity(item.quantity()).toPlainString(),
                    MdmMasterDataRules.id(item.uomId(), "uomId")));
        }
        validated.sort(java.util.Comparator.comparing(FormulaModels.ItemInput::sequenceNo));
        return List.copyOf(validated);
    }

    /** 数量字段只接受可由 numeric(24,9) 精确保存的正 Decimal String。 */
    private static BigDecimal quantity(String value) {
        if (value == null || !value.matches("(0|[1-9][0-9]{0,14})(\\.[0-9]{1,9})?")) {
            throw MdmException.invalidReference();
        }
        BigDecimal quantity = new BigDecimal(value);
        if (quantity.signum() <= 0) throw MdmException.invalidReference();
        return quantity;
    }

    private static FormulaRevisionEntity newRevision(String formulaId, int number) {
        FormulaRevisionEntity revision = new FormulaRevisionEntity();
        revision.setFormulaId(formulaId);
        revision.setRevisionNo(number);
        revision.setStatus(DRAFT);
        return revision;
    }

    private void insertItems(String revisionId, List<FormulaModels.ItemInput> items) {
        for (FormulaModels.ItemInput input : items) {
            FormulaItemEntity item = new FormulaItemEntity();
            item.setRevisionId(revisionId);
            item.setSequenceNo(input.sequenceNo());
            item.setMaterialId(input.materialId());
            item.setQuantity(quantity(input.quantity()));
            item.setUomId(input.uomId());
            itemMapper.insert(item);
        }
    }

    private List<FormulaItemEntity> loadItems(String revisionId) {
        return itemMapper.selectList(new LambdaQueryWrapper<FormulaItemEntity>()
                .eq(FormulaItemEntity::getRevisionId, revisionId)
                .orderByAsc(FormulaItemEntity::getSequenceNo));
    }

    private FormulaRevisionEntity latestRevision(String formulaId) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<FormulaRevisionEntity>()
                .eq(FormulaRevisionEntity::getFormulaId, formulaId)
                .orderByDesc(FormulaRevisionEntity::getRevisionNo).last("LIMIT 1"));
    }

    private FormulaEntity requireFormula(String id) {
        FormulaEntity formula = formulaMapper.selectById(MdmMasterDataRules.id(id, "formulaId"));
        if (formula == null) throw MdmException.notFound("Formula");
        return formula;
    }

    private FormulaRevisionEntity requireRevision(String id) {
        FormulaRevisionEntity revision = revisionMapper.selectById(MdmMasterDataRules.id(id, "revisionId"));
        if (revision == null) throw MdmException.notFound("FormulaRevision");
        return revision;
    }

    private FormulaRevisionEntity requireRevisionForUpdate(String id) {
        FormulaRevisionEntity revision = revisionMapper.selectByIdForUpdate(
                MdmMasterDataRules.id(id, "revisionId"));
        if (revision == null) throw MdmException.notFound("FormulaRevision");
        return revision;
    }

    private static void requireDraft(FormulaRevisionEntity revision) {
        if (!DRAFT.equals(revision.getStatus())) throw MdmException.immutable();
    }

    private void requireUpdated(int affected, String id) {
        if (affected == 1) return;
        if (revisionMapper.selectById(id) == null) throw MdmException.notFound("FormulaRevision");
        throw MdmException.versionConflict();
    }

    private FormulaModels.DetailView detail(FormulaEntity formula, FormulaRevisionEntity revision) {
        List<FormulaModels.ItemView> items = loadItems(revision.getId()).stream()
                .map(item -> new FormulaModels.ItemView(item.getId(), item.getSequenceNo(),
                        item.getMaterialId(), item.getQuantity().toPlainString(), item.getUomId()))
                .toList();
        return new FormulaModels.DetailView(formulaView(formula), revisionView(revision), items);
    }

    private static FormulaModels.FormulaView formulaView(FormulaEntity formula) {
        return new FormulaModels.FormulaView(formula.getId(), formula.getCode(), formula.getName(),
                formula.getPlantId(), formula.getOutputMaterialId(),
                formula.getBaseBatchQuantity().toPlainString(), formula.getBaseUomId(),
                formula.getCreatedAt(), formula.getCreatedBy(), formula.getUpdatedAt(), formula.getUpdatedBy());
    }

    private static FormulaModels.RevisionView revisionView(FormulaRevisionEntity revision) {
        return new FormulaModels.RevisionView(revision.getId(), revision.getFormulaId(),
                revision.getRevisionNo(), revision.getStatus(), revision.getLockVersion(),
                revision.getReleasedAt(), revision.getObsoleteAt(),
                revision.getCreatedAt(), revision.getCreatedBy(), revision.getUpdatedAt(), revision.getUpdatedBy());
    }
}
