package io.github.chrisshi.mom.mdm.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.chrisshi.mom.mdm.application.model.QualityModels;
import io.github.chrisshi.mom.mdm.api.QualitySpecificationReference;
import io.github.chrisshi.mom.mdm.infrastructure.entity.InspectionCharacteristicEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.InspectionMethodEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.QualitySpecificationEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.SpecificationItemEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.SpecificationRevisionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.InspectionCharacteristicMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.InspectionMethodMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.QualitySpecificationMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.SpecificationItemMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.SpecificationRevisionMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * MDM 质量定义的单数据库事务写入边界。
 *
 * <p>工厂、物料、单位、特性和方法均在 mom_mdm 同一本地事务内校验并按稳定顺序锁定。
 * 该边界不访问 QMS；已发布修订不可变，后续修正只能产生新业务修订。</p>
 */
@Component
public class QualityDefinitionWriter {
    private static final String DRAFT = "DRAFT";
    private static final String RELEASED = "RELEASED";
    private static final String OBSOLETE = "OBSOLETE";

    private final InspectionCharacteristicMapper characteristics;
    private final InspectionMethodMapper methods;
    private final QualitySpecificationMapper specifications;
    private final SpecificationRevisionMapper revisions;
    private final SpecificationItemMapper items;
    private final QualityDefinitionReferenceValidator references;

    public QualityDefinitionWriter(InspectionCharacteristicMapper characteristics, InspectionMethodMapper methods,
                                   QualitySpecificationMapper specifications, SpecificationRevisionMapper revisions,
                                   SpecificationItemMapper items, QualityDefinitionReferenceValidator references) {
        this.characteristics = characteristics;
        this.methods = methods;
        this.specifications = specifications;
        this.revisions = revisions;
        this.items = items;
        this.references = references;
    }

    /** 校验默认单位并创建数值型检验特性。 */
    @Transactional
    public QualityModels.CharacteristicView createCharacteristic(String code, String name, String uomId) {
        QualityModels.UomSnapshot uom = references.requireUom(uomId);
        InspectionCharacteristicEntity entity = new InspectionCharacteristicEntity();
        entity.setCode(QualityDefinitionRules.code(code));
        entity.setName(QualityDefinitionRules.name(name));
        entity.setDefaultUomId(uom.id());
        entity.setDefaultUomVersion(uom.version());
        entity.setEnabled(true);
        try {
            characteristics.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw MdmException.codeConflict("inspection characteristic");
        }
        return characteristic(entity);
    }

    /** 锁定特性与默认单位，以技术版本 CAS 更新。 */
    @Transactional
    public QualityModels.CharacteristicView updateCharacteristic(String id, String name, String uomId,
                                                                  Boolean enabled, Long version) {
        InspectionCharacteristicEntity entity = requireCharacteristicForUpdate(id);
        QualityDefinitionRules.requireVersion(entity.getVersion(), version);
        QualityModels.UomSnapshot uom = references.requireUom(uomId);
        entity.setName(QualityDefinitionRules.name(name));
        entity.setEnabled(enabled);
        entity.setDefaultUomId(uom.id());
        entity.setDefaultUomVersion(uom.version());
        if (characteristics.updateById(entity) != 1) throw MdmException.versionConflict();
        return characteristic(entity);
    }

    /** 创建检验方法静态定义。 */
    @Transactional
    public QualityModels.MethodView createMethod(String code, String name, String description) {
        InspectionMethodEntity entity = new InspectionMethodEntity();
        entity.setCode(QualityDefinitionRules.code(code));
        entity.setName(QualityDefinitionRules.name(name));
        entity.setDescription(optionalDescription(description));
        entity.setEnabled(true);
        try {
            methods.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw MdmException.codeConflict("inspection method");
        }
        return method(entity);
    }

    /** 锁定检验方法并以技术版本 CAS 更新。 */
    @Transactional
    public QualityModels.MethodView updateMethod(String id, String name, String description,
                                                 Boolean enabled, Long version) {
        InspectionMethodEntity entity = requireMethodForUpdate(id);
        QualityDefinitionRules.requireVersion(entity.getVersion(), version);
        entity.setName(QualityDefinitionRules.name(name));
        entity.setDescription(optionalDescription(description));
        entity.setEnabled(enabled);
        if (methods.updateById(entity) != 1) throw MdmException.versionConflict();
        return method(entity);
    }

    /** 原子校验引用并创建标准身份、第一草稿修订和有序条目。 */
    @Transactional
    public QualityModels.DetailView createSpecification(String code, String name, String plantId, String materialId,
                                                        List<QualityModels.ItemInput> inputs) {
        QualityModels.ScopeSnapshot scope = references.requireScope(plantId, materialId);
        List<QualityModels.PreparedItem> prepared = prepare(inputs);
        QualitySpecificationEntity specification = new QualitySpecificationEntity();
        specification.setCode(QualityDefinitionRules.code(code));
        specification.setName(QualityDefinitionRules.name(name));
        specification.setPlantId(scope.plantId());
        specification.setMaterialId(scope.materialId());
        specification.setInspectionUsage("INCOMING");
        applyScopeSnapshot(specification, scope);
        try {
            specifications.insert(specification);
        } catch (DuplicateKeyException exception) {
            throw MdmException.codeConflict("quality specification code or scope");
        }
        SpecificationRevisionEntity revision = newRevision(specification.getId(), 1);
        revisions.insert(revision);
        insertItems(revision.getId(), prepared);
        return detail(specification, revision);
    }

    /** 仅草稿允许在本地事务内整体替换检验项。 */
    @Transactional
    public QualityModels.DetailView updateDraft(String revisionId, Long lockVersion,
                                                List<QualityModels.ItemInput> inputs) {
        SpecificationRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireStatus(revision, DRAFT);
        QualityDefinitionRules.requireVersion(revision.getLockVersion(), lockVersion);
        List<QualityModels.PreparedItem> prepared = prepare(inputs);
        items.delete(new LambdaQueryWrapper<SpecificationItemEntity>()
                .eq(SpecificationItemEntity::getRevisionId, revision.getId()));
        insertItems(revision.getId(), prepared);
        if (revisions.updateById(revision) != 1) throw MdmException.versionConflict();
        return detail(requireSpecification(revision.getSpecificationId()), revision);
    }

    /** 从最后一个不可变修订创建下一草稿，并重新校验所有引用。 */
    @Transactional
    public QualityModels.DetailView nextRevision(String specificationId) {
        QualitySpecificationEntity specification = specifications.selectByIdForUpdate(
                QualityDefinitionRules.id(specificationId));
        if (specification == null) throw MdmException.notFound("quality specification");
        SpecificationRevisionEntity latest = latest(specification.getId());
        if (latest == null || DRAFT.equals(latest.getStatus())) throw MdmException.immutable();
        List<QualityModels.PreparedItem> prepared = prepare(toInputs(loadItems(latest.getId())));
        SpecificationRevisionEntity next = newRevision(specification.getId(), latest.getRevisionNo() + 1);
        revisions.insert(next);
        insertItems(next.getId(), prepared);
        return detail(specification, next);
    }

    /** 重新校验范围与全部条目，发布后修订不可变。 */
    @Transactional
    public QualityModels.DetailView release(String revisionId, Long lockVersion) {
        SpecificationRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireStatus(revision, DRAFT);
        QualityDefinitionRules.requireVersion(revision.getLockVersion(), lockVersion);
        QualitySpecificationEntity specification = specifications.selectByIdForUpdate(revision.getSpecificationId());
        if (specification == null) throw MdmException.notFound("quality specification");
        QualityModels.ScopeSnapshot scope = references.requireScope(
                specification.getPlantId(), specification.getMaterialId());
        List<QualityModels.PreparedItem> prepared = prepare(toInputs(loadItems(revision.getId())));
        Long releasedCount = revisions.selectCount(new LambdaQueryWrapper<SpecificationRevisionEntity>()
                .eq(SpecificationRevisionEntity::getSpecificationId, specification.getId())
                .eq(SpecificationRevisionEntity::getStatus, RELEASED));
        if (releasedCount > 0) throw MdmException.codeConflict("released quality specification revision");
        applyScopeSnapshot(specification, scope);
        specifications.updateById(specification);
        refreshItems(revision.getId(), prepared);
        revision.setStatus(RELEASED);
        revision.setReleasedAt(Instant.now());
        if (revisions.updateById(revision) != 1) throw MdmException.versionConflict();
        return detail(specification, revision);
    }

    /** 废止已发布修订，历史快照保持可读。 */
    @Transactional
    public QualityModels.DetailView obsolete(String revisionId, Long lockVersion) {
        SpecificationRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireStatus(revision, RELEASED);
        QualityDefinitionRules.requireVersion(revision.getLockVersion(), lockVersion);
        revision.setStatus(OBSOLETE);
        revision.setObsoleteAt(Instant.now());
        if (revisions.updateById(revision) != 1) throw MdmException.versionConflict();
        return detail(requireSpecification(revision.getSpecificationId()), revision);
    }

    /** 按标准身份读取最新修订。 */
    @Transactional(readOnly = true)
    public QualityModels.DetailView detailBySpecification(String id) {
        QualitySpecificationEntity specification = requireSpecification(id);
        SpecificationRevisionEntity revision = latest(specification.getId());
        if (revision == null) throw MdmException.notFound("quality specification revision");
        return detail(specification, revision);
    }

    /** 按修订身份读取标准详情。 */
    @Transactional(readOnly = true)
    public QualityModels.DetailView detailByRevision(String id) {
        SpecificationRevisionEntity revision = requireRevision(id);
        return detail(requireSpecification(revision.getSpecificationId()), revision);
    }

    /** 返回当前已发布修订快照及新业务可用性，不修改历史定义。 */
    @Transactional(readOnly = true)
    public QualitySpecificationReference reference(String id) {
        QualitySpecificationEntity specification = requireSpecification(id);
        SpecificationRevisionEntity released = revisions.selectOne(
                new LambdaQueryWrapper<SpecificationRevisionEntity>()
                        .eq(SpecificationRevisionEntity::getSpecificationId, specification.getId())
                        .eq(SpecificationRevisionEntity::getStatus, RELEASED));
        if (released == null) {
            return qualityReference(specification, null, List.of(), "QUALITY_SPECIFICATION_NOT_RELEASED");
        }
        List<SpecificationItemEntity> releasedItems = loadItems(released.getId());
        String reason = references.scopeInvalidReason(specification.getPlantId(), specification.getMaterialId());
        if (reason == null) reason = itemInvalidReason(releasedItems);
        return qualityReference(specification, released, releasedItems, reason);
    }

    private List<QualityModels.PreparedItem> prepare(List<QualityModels.ItemInput> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 200) throw MdmException.invalidReference();
        Set<Integer> sequences = new HashSet<>();
        Map<String, InspectionCharacteristicEntity> lockedCharacteristics = new TreeMap<>();
        Map<String, InspectionMethodEntity> lockedMethods = new TreeMap<>();

        for (QualityModels.ItemInput input : inputs) {
            if (input == null || input.sequenceNo() == null || input.sequenceNo() <= 0
                    || !sequences.add(input.sequenceNo())) throw MdmException.invalidReference();
            lockedCharacteristics.put(QualityDefinitionRules.id(input.characteristicId()), null);
            lockedMethods.put(QualityDefinitionRules.id(input.methodId()), null);
            validateRange(input.lowerLimit(), input.targetValue(), input.upperLimit());
        }

        for (String id : List.copyOf(lockedCharacteristics.keySet())) {
            InspectionCharacteristicEntity entity = characteristics.selectByIdForUpdate(id);
            if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
                throw MdmException.parentDisabled("inspection characteristic");
            }
            lockedCharacteristics.put(id, entity);
        }
        for (String id : List.copyOf(lockedMethods.keySet())) {
            InspectionMethodEntity entity = methods.selectByIdForUpdate(id);
            if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
                throw MdmException.parentDisabled("inspection method");
            }
            lockedMethods.put(id, entity);
        }

        Set<String> uomIds = new HashSet<>();
        for (QualityModels.ItemInput input : inputs) {
            uomIds.add(QualityDefinitionRules.id(input.uomId()));
            uomIds.add(lockedCharacteristics.get(QualityDefinitionRules.id(input.characteristicId())).getDefaultUomId());
        }
        Map<String, QualityModels.UomSnapshot> uomSnapshots = references.requireUoms(uomIds);
        List<QualityModels.PreparedItem> prepared = new ArrayList<>(inputs.size());
        for (QualityModels.ItemInput input : inputs) {
            String characteristicId = QualityDefinitionRules.id(input.characteristicId());
            String methodId = QualityDefinitionRules.id(input.methodId());
            String uomId = QualityDefinitionRules.id(input.uomId());
            InspectionCharacteristicEntity characteristic = lockedCharacteristics.get(characteristicId);
            InspectionMethodEntity method = lockedMethods.get(methodId);
            QualityModels.UomSnapshot defaultUom = uomSnapshots.get(characteristic.getDefaultUomId());
            QualityModels.UomSnapshot itemUom = uomSnapshots.get(uomId);
            if (defaultUom == null || itemUom == null || !defaultUom.dimensionId().equals(itemUom.dimensionId())) {
                throw MdmException.incompatibleUom();
            }
            prepared.add(new QualityModels.PreparedItem(input.sequenceNo(), characteristicId,
                    characteristic.getVersion(), methodId, method.getVersion(), itemUom.id(), itemUom.version(),
                    input.lowerLimit(), input.targetValue(), input.upperLimit(), !Boolean.FALSE.equals(input.mandatory())));
        }
        return List.copyOf(prepared);
    }

    private String itemInvalidReason(List<SpecificationItemEntity> source) {
        for (SpecificationItemEntity item : source) {
            InspectionCharacteristicEntity characteristic = characteristics.selectById(item.getCharacteristicId());
            if (characteristic == null) return "CHARACTERISTIC_MISSING";
            if (!Boolean.TRUE.equals(characteristic.getEnabled())) return "CHARACTERISTIC_DISABLED";
            InspectionMethodEntity method = methods.selectById(item.getMethodId());
            if (method == null) return "METHOD_MISSING";
            if (!Boolean.TRUE.equals(method.getEnabled())) return "METHOD_DISABLED";
            String reason = references.uomInvalidReason(item.getUomId());
            if (reason != null) return reason;
        }
        return null;
    }

    private static QualitySpecificationReference qualityReference(QualitySpecificationEntity specification,
            SpecificationRevisionEntity revision, List<SpecificationItemEntity> source, String reason) {
        List<QualitySpecificationReference.Item> result = source.stream().map(item ->
                new QualitySpecificationReference.Item(item.getSequenceNo(), item.getCharacteristicId(),
                        item.getCharacteristicVersion(), item.getMethodId(), item.getMethodVersion(), item.getUomId(),
                        item.getUomVersion(), decimal(item.getLowerLimit()), decimal(item.getTargetValue()),
                        decimal(item.getUpperLimit()), Boolean.TRUE.equals(item.getMandatory()))).toList();
        return new QualitySpecificationReference(specification.getId(), specification.getCode(), specification.getName(),
                specification.getPlantId(), specification.getMaterialId(), specification.getInspectionUsage(),
                revision == null ? null : revision.getId(), revision == null ? null : revision.getRevisionNo(),
                revision == null ? null : revision.getReleasedAt(), result, reason == null, reason);
    }

    private static void validateRange(String lower, String target, String upper) {
        BigDecimal low = QualityDefinitionRules.optionalDecimal(lower);
        BigDecimal goal = QualityDefinitionRules.optionalDecimal(target);
        BigDecimal high = QualityDefinitionRules.optionalDecimal(upper);
        if (low == null && goal == null && high == null) throw MdmException.qualityInvalidRange();
        if ((low != null && goal != null && low.compareTo(goal) > 0)
                || (goal != null && high != null && goal.compareTo(high) > 0)
                || (low != null && high != null && low.compareTo(high) > 0)) {
            throw MdmException.qualityInvalidRange();
        }
    }

    private void refreshItems(String revisionId, List<QualityModels.PreparedItem> prepared) {
        Map<Integer, SpecificationItemEntity> existing = new LinkedHashMap<>();
        loadItems(revisionId).forEach(item -> existing.put(item.getSequenceNo(), item));
        if (existing.size() != prepared.size()) throw MdmException.invalidReference();
        for (QualityModels.PreparedItem input : prepared) {
            SpecificationItemEntity entity = existing.get(input.sequenceNo());
            if (entity == null) throw MdmException.invalidReference();
            entity.setCharacteristicId(input.characteristicId());
            entity.setCharacteristicVersion(input.characteristicVersion());
            entity.setMethodId(input.methodId());
            entity.setMethodVersion(input.methodVersion());
            entity.setUomId(input.uomId());
            entity.setUomVersion(input.uomVersion());
            entity.setLowerLimit(QualityDefinitionRules.optionalDecimal(input.lowerLimit()));
            entity.setTargetValue(QualityDefinitionRules.optionalDecimal(input.targetValue()));
            entity.setUpperLimit(QualityDefinitionRules.optionalDecimal(input.upperLimit()));
            entity.setMandatory(input.mandatory());
            if (items.updateById(entity) != 1) throw MdmException.versionConflict();
        }
    }

    private void insertItems(String revisionId, List<QualityModels.PreparedItem> prepared) {
        for (QualityModels.PreparedItem input : prepared) {
            SpecificationItemEntity entity = new SpecificationItemEntity();
            entity.setRevisionId(revisionId);
            entity.setSequenceNo(input.sequenceNo());
            entity.setCharacteristicId(input.characteristicId());
            entity.setCharacteristicVersion(input.characteristicVersion());
            entity.setMethodId(input.methodId());
            entity.setMethodVersion(input.methodVersion());
            entity.setUomId(input.uomId());
            entity.setUomVersion(input.uomVersion());
            entity.setLowerLimit(QualityDefinitionRules.optionalDecimal(input.lowerLimit()));
            entity.setTargetValue(QualityDefinitionRules.optionalDecimal(input.targetValue()));
            entity.setUpperLimit(QualityDefinitionRules.optionalDecimal(input.upperLimit()));
            entity.setMandatory(input.mandatory());
            items.insert(entity);
        }
    }

    private static void applyScopeSnapshot(QualitySpecificationEntity entity, QualityModels.ScopeSnapshot snapshot) {
        entity.setPlantVersion(snapshot.plantVersion());
        entity.setMaterialRevisionNo(snapshot.materialRevisionNo());
        entity.setMaterialVersion(snapshot.materialVersion());
    }

    private static SpecificationRevisionEntity newRevision(String specificationId, int revisionNo) {
        SpecificationRevisionEntity entity = new SpecificationRevisionEntity();
        entity.setSpecificationId(specificationId);
        entity.setRevisionNo(revisionNo);
        entity.setStatus(DRAFT);
        return entity;
    }

    private InspectionCharacteristicEntity requireCharacteristicForUpdate(String id) {
        InspectionCharacteristicEntity entity = characteristics.selectByIdForUpdate(QualityDefinitionRules.id(id));
        if (entity == null) throw MdmException.notFound("inspection characteristic");
        return entity;
    }

    private InspectionMethodEntity requireMethodForUpdate(String id) {
        InspectionMethodEntity entity = methods.selectByIdForUpdate(QualityDefinitionRules.id(id));
        if (entity == null) throw MdmException.notFound("inspection method");
        return entity;
    }

    private QualitySpecificationEntity requireSpecification(String id) {
        QualitySpecificationEntity entity = specifications.selectById(QualityDefinitionRules.id(id));
        if (entity == null) throw MdmException.notFound("quality specification");
        return entity;
    }

    private SpecificationRevisionEntity requireRevision(String id) {
        SpecificationRevisionEntity entity = revisions.selectById(QualityDefinitionRules.id(id));
        if (entity == null) throw MdmException.notFound("quality specification revision");
        return entity;
    }

    private SpecificationRevisionEntity requireRevisionForUpdate(String id) {
        SpecificationRevisionEntity entity = revisions.selectByIdForUpdate(QualityDefinitionRules.id(id));
        if (entity == null) throw MdmException.notFound("quality specification revision");
        return entity;
    }

    private SpecificationRevisionEntity latest(String specificationId) {
        return revisions.selectOne(new LambdaQueryWrapper<SpecificationRevisionEntity>()
                .eq(SpecificationRevisionEntity::getSpecificationId, specificationId)
                .orderByDesc(SpecificationRevisionEntity::getRevisionNo)
                .last("LIMIT 1"));
    }

    private List<SpecificationItemEntity> loadItems(String revisionId) {
        return items.selectList(new LambdaQueryWrapper<SpecificationItemEntity>()
                .eq(SpecificationItemEntity::getRevisionId, revisionId)
                .orderByAsc(SpecificationItemEntity::getSequenceNo));
    }

    private QualityModels.DetailView detail(QualitySpecificationEntity specification,
                                            SpecificationRevisionEntity revision) {
        List<QualityModels.ItemView> itemViews = loadItems(revision.getId()).stream().map(item ->
                new QualityModels.ItemView(item.getId(), item.getSequenceNo(), item.getCharacteristicId(),
                        item.getCharacteristicVersion(), item.getMethodId(), item.getMethodVersion(), item.getUomId(),
                        item.getUomVersion(), decimal(item.getLowerLimit()), decimal(item.getTargetValue()),
                        decimal(item.getUpperLimit()), Boolean.TRUE.equals(item.getMandatory()))).toList();
        return new QualityModels.DetailView(specification.getId(), specification.getCode(), specification.getName(),
                specification.getPlantId(), specification.getMaterialId(), specification.getInspectionUsage(),
                specification.getPlantVersion(), specification.getMaterialRevisionNo(), specification.getMaterialVersion(),
                revision.getId(), revision.getRevisionNo(), revision.getStatus(), revision.getLockVersion(),
                revision.getReleasedAt(), revision.getObsoleteAt(), itemViews);
    }

    private static List<QualityModels.ItemInput> toInputs(List<SpecificationItemEntity> source) {
        return source.stream().map(item -> new QualityModels.ItemInput(item.getSequenceNo(), item.getCharacteristicId(),
                item.getMethodId(), item.getUomId(), decimal(item.getLowerLimit()), decimal(item.getTargetValue()),
                decimal(item.getUpperLimit()), item.getMandatory())).toList();
    }

    private static void requireStatus(SpecificationRevisionEntity entity, String expected) {
        if (!expected.equals(entity.getStatus())) throw MdmException.immutable();
    }

    private static String optionalDescription(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static QualityModels.CharacteristicView characteristic(InspectionCharacteristicEntity entity) {
        return new QualityModels.CharacteristicView(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDefaultUomId(), entity.getDefaultUomVersion(), Boolean.TRUE.equals(entity.getEnabled()),
                entity.getVersion());
    }

    private static QualityModels.MethodView method(InspectionMethodEntity entity) {
        return new QualityModels.MethodView(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion());
    }
}
