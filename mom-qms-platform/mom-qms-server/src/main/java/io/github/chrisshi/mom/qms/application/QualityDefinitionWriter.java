package io.github.chrisshi.mom.qms.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.chrisshi.mom.qms.application.model.QualityModels;
import io.github.chrisshi.mom.qms.infrastructure.entity.*;
import io.github.chrisshi.mom.qms.infrastructure.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * QMS 单数据库事务写入边界。
 *
 * <p>所有 MDM 网络校验必须在进入本 Bean 前完成；提交时只锁定和写入 mom_qms Schema，
 * 并重新校验本地特性、方法及技术版本，防止校验与提交之间引用被停用。</p>
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

    public QualityDefinitionWriter(InspectionCharacteristicMapper characteristics, InspectionMethodMapper methods,
                                   QualitySpecificationMapper specifications, SpecificationRevisionMapper revisions,
                                   SpecificationItemMapper items) {
        this.characteristics = characteristics; this.methods = methods; this.specifications = specifications;
        this.revisions = revisions; this.items = items;
    }

    /** 创建数值特性；UOM 已在事务外由 MDM 校验。 */
    @Transactional
    public QualityModels.CharacteristicView createCharacteristic(String code, String name,
            QualityModels.UomSnapshot uom) {
        InspectionCharacteristicEntity entity = new InspectionCharacteristicEntity();
        entity.setCode(QmsRules.code(code)); entity.setName(QmsRules.name(name));
        entity.setDefaultUomId(uom.id()); entity.setDefaultUomVersion(uom.version()); entity.setEnabled(true);
        try { characteristics.insert(entity); } catch (DuplicateKeyException exception) { throw QmsException.conflict(); }
        return characteristic(entity);
    }

    /** 更新特性并以 CAS 防止覆盖并发修改。 */
    @Transactional
    public QualityModels.CharacteristicView updateCharacteristic(String id, String name, Boolean enabled,
            Long version, QualityModels.UomSnapshot uom) {
        InspectionCharacteristicEntity entity = requireCharacteristic(QmsRules.id(id));
        QmsRules.requireVersion(entity.getVersion(), version);
        entity.setName(QmsRules.name(name)); entity.setEnabled(enabled);
        entity.setDefaultUomId(uom.id()); entity.setDefaultUomVersion(uom.version());
        if (characteristics.updateById(entity) != 1) throw QmsException.versionConflict();
        return characteristic(entity);
    }

    /** 创建检验方法。 */
    @Transactional
    public QualityModels.MethodView createMethod(String code, String name, String description) {
        InspectionMethodEntity entity = new InspectionMethodEntity();
        entity.setCode(QmsRules.code(code)); entity.setName(QmsRules.name(name));
        entity.setDescription(description == null || description.isBlank() ? null : description.trim()); entity.setEnabled(true);
        try { methods.insert(entity); } catch (DuplicateKeyException exception) { throw QmsException.conflict(); }
        return method(entity);
    }

    /** 更新检验方法和启停状态。 */
    @Transactional
    public QualityModels.MethodView updateMethod(String id, String name, String description, Boolean enabled, Long version) {
        InspectionMethodEntity entity = requireMethod(QmsRules.id(id));
        QmsRules.requireVersion(entity.getVersion(), version);
        entity.setName(QmsRules.name(name)); entity.setDescription(description == null || description.isBlank() ? null : description.trim());
        entity.setEnabled(enabled);
        if (methods.updateById(entity) != 1) throw QmsException.versionConflict();
        return method(entity);
    }

    /** 原子创建标准身份、第一草稿修订和有序条目。 */
    @Transactional
    public QualityModels.DetailView createSpecification(String code, String name, QualityModels.MdmSnapshot mdm,
            List<QualityModels.PreparedItem> prepared) {
        QualitySpecificationEntity specification = new QualitySpecificationEntity();
        specification.setCode(QmsRules.code(code)); specification.setName(QmsRules.name(name));
        specification.setPlantId(mdm.plantId()); specification.setMaterialId(mdm.materialId());
        specification.setInspectionUsage("INCOMING"); applyMdmSnapshot(specification, mdm);
        try { specifications.insert(specification); } catch (DuplicateKeyException exception) { throw QmsException.conflict(); }
        SpecificationRevisionEntity revision = newRevision(specification.getId(), 1);
        revisions.insert(revision); insertItems(revision.getId(), prepared);
        return detail(specification, revision);
    }

    /** 仅草稿可整体替换条目；本地引用版本在同一事务中重验。 */
    @Transactional
    public QualityModels.DetailView updateDraft(String revisionId, Long lockVersion,
            List<QualityModels.PreparedItem> prepared) {
        SpecificationRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireStatus(revision, DRAFT); QmsRules.requireVersion(revision.getLockVersion(), lockVersion);
        validateLocalReferences(prepared);
        items.delete(new LambdaQueryWrapper<SpecificationItemEntity>().eq(SpecificationItemEntity::getRevisionId, revision.getId()));
        insertItems(revision.getId(), prepared);
        if (revisions.updateById(revision) != 1) throw QmsException.versionConflict();
        return detail(requireSpecification(revision.getSpecificationId()), revision);
    }

    /** 从最后一个不可变修订创建下一草稿。 */
    @Transactional
    public QualityModels.DetailView nextRevision(String specificationId, List<QualityModels.PreparedItem> prepared) {
        QualitySpecificationEntity specification = specifications.selectByIdForUpdate(QmsRules.id(specificationId));
        if (specification == null) throw QmsException.notFound();
        SpecificationRevisionEntity latest = latest(specification.getId());
        if (latest == null || DRAFT.equals(latest.getStatus())) throw QmsException.immutable();
        validateLocalReferences(prepared);
        SpecificationRevisionEntity next = newRevision(specification.getId(), latest.getRevisionNo() + 1);
        revisions.insert(next); insertItems(next.getId(), prepared);
        return detail(specification, next);
    }

    /** 发布时更新 MDM 快照并保证同一标准最多一个 RELEASED 修订。 */
    @Transactional
    public QualityModels.DetailView release(String revisionId, Long lockVersion, QualityModels.MdmSnapshot mdm,
            List<QualityModels.PreparedItem> prepared) {
        SpecificationRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireStatus(revision, DRAFT); QmsRules.requireVersion(revision.getLockVersion(), lockVersion);
        QualitySpecificationEntity specification = specifications.selectByIdForUpdate(revision.getSpecificationId());
        if (specification == null || !specification.getPlantId().equals(mdm.plantId())
                || !specification.getMaterialId().equals(mdm.materialId())) throw QmsException.invalidReference();
        Long releasedCount = revisions.selectCount(new LambdaQueryWrapper<SpecificationRevisionEntity>()
                .eq(SpecificationRevisionEntity::getSpecificationId, specification.getId())
                .eq(SpecificationRevisionEntity::getStatus, RELEASED));
        if (releasedCount > 0) throw QmsException.conflict();
        validateLocalReferences(prepared); applyMdmSnapshot(specification, mdm); specifications.updateById(specification);
        revision.setStatus(RELEASED); revision.setReleasedAt(Instant.now());
        if (revisions.updateById(revision) != 1) throw QmsException.versionConflict();
        return detail(specification, revision);
    }

    /** 废止已发布修订，历史定义保持可读。 */
    @Transactional
    public QualityModels.DetailView obsolete(String revisionId, Long lockVersion) {
        SpecificationRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireStatus(revision, RELEASED); QmsRules.requireVersion(revision.getLockVersion(), lockVersion);
        revision.setStatus(OBSOLETE); revision.setObsoleteAt(Instant.now());
        if (revisions.updateById(revision) != 1) throw QmsException.versionConflict();
        return detail(requireSpecification(revision.getSpecificationId()), revision);
    }

    /** 读取准备条目所需的本地引用快照，网络调用不在此方法内发生。 */
    @Transactional(readOnly = true)
    public QualityModels.LocalItemReference localReference(String characteristicId, String methodId) {
        InspectionCharacteristicEntity characteristic = requireCharacteristic(QmsRules.id(characteristicId));
        InspectionMethodEntity method = requireMethod(QmsRules.id(methodId));
        if (!Boolean.TRUE.equals(characteristic.getEnabled()) || !Boolean.TRUE.equals(method.getEnabled())) {
            throw QmsException.disabledReference();
        }
        return new QualityModels.LocalItemReference(characteristic.getId(), characteristic.getVersion(),
                characteristic.getDefaultUomId(), method.getId(), method.getVersion());
    }

    /** 按修订读取条目以支持创建下一修订和发布前重验。 */
    @Transactional(readOnly = true)
    public List<QualityModels.ItemInput> itemInputs(String revisionId) {
        requireRevision(revisionId);
        return loadItems(revisionId).stream().map(item -> new QualityModels.ItemInput(item.getSequenceNo(),
                item.getCharacteristicId(), item.getMethodId(), item.getUomId(), decimal(item.getLowerLimit()),
                decimal(item.getTargetValue()), decimal(item.getUpperLimit()), item.getMandatory())).toList();
    }

    @Transactional(readOnly = true)
    public QualityModels.DetailView detailBySpecification(String id) {
        QualitySpecificationEntity specification = requireSpecification(id);
        SpecificationRevisionEntity revision = latest(specification.getId());
        if (revision == null) throw QmsException.notFound();
        return detail(specification, revision);
    }

    @Transactional(readOnly = true)
    public QualityModels.DetailView detailByRevision(String id) {
        SpecificationRevisionEntity revision = requireRevision(id);
        return detail(requireSpecification(revision.getSpecificationId()), revision);
    }

    private void validateLocalReferences(List<QualityModels.PreparedItem> prepared) {
        for (QualityModels.PreparedItem item : prepared) {
            InspectionCharacteristicEntity characteristic = requireCharacteristic(item.characteristicId());
            InspectionMethodEntity method = requireMethod(item.methodId());
            if (!Boolean.TRUE.equals(characteristic.getEnabled()) || !Boolean.TRUE.equals(method.getEnabled())
                    || !characteristic.getVersion().equals(item.characteristicVersion())
                    || !method.getVersion().equals(item.methodVersion())) throw QmsException.disabledReference();
        }
    }

    private void insertItems(String revisionId, List<QualityModels.PreparedItem> prepared) {
        validateLocalReferences(prepared);
        for (QualityModels.PreparedItem input : prepared) {
            SpecificationItemEntity entity = new SpecificationItemEntity();
            entity.setRevisionId(revisionId); entity.setSequenceNo(input.sequenceNo());
            entity.setCharacteristicId(input.characteristicId()); entity.setCharacteristicVersion(input.characteristicVersion());
            entity.setMethodId(input.methodId()); entity.setMethodVersion(input.methodVersion());
            entity.setUomId(input.uomId()); entity.setUomVersion(input.uomVersion());
            entity.setLowerLimit(QmsRules.optionalDecimal(input.lowerLimit()));
            entity.setTargetValue(QmsRules.optionalDecimal(input.targetValue()));
            entity.setUpperLimit(QmsRules.optionalDecimal(input.upperLimit())); entity.setMandatory(input.mandatory());
            items.insert(entity);
        }
    }

    private static void applyMdmSnapshot(QualitySpecificationEntity entity, QualityModels.MdmSnapshot snapshot) {
        entity.setPlantVersion(snapshot.plantVersion()); entity.setMaterialRevisionNo(snapshot.materialRevisionNo());
        entity.setMaterialVersion(snapshot.materialVersion());
    }
    private static SpecificationRevisionEntity newRevision(String specificationId, int revisionNo) {
        SpecificationRevisionEntity entity = new SpecificationRevisionEntity();
        entity.setSpecificationId(specificationId); entity.setRevisionNo(revisionNo); entity.setStatus(DRAFT); return entity;
    }
    private InspectionCharacteristicEntity requireCharacteristic(String id) {
        InspectionCharacteristicEntity entity = characteristics.selectById(QmsRules.id(id));
        if (entity == null) throw QmsException.notFound(); return entity;
    }
    private InspectionMethodEntity requireMethod(String id) {
        InspectionMethodEntity entity = methods.selectById(QmsRules.id(id));
        if (entity == null) throw QmsException.notFound(); return entity;
    }
    private QualitySpecificationEntity requireSpecification(String id) {
        QualitySpecificationEntity entity = specifications.selectById(QmsRules.id(id));
        if (entity == null) throw QmsException.notFound(); return entity;
    }
    private SpecificationRevisionEntity requireRevision(String id) {
        SpecificationRevisionEntity entity = revisions.selectById(QmsRules.id(id));
        if (entity == null) throw QmsException.notFound(); return entity;
    }
    private SpecificationRevisionEntity requireRevisionForUpdate(String id) {
        SpecificationRevisionEntity entity = revisions.selectByIdForUpdate(QmsRules.id(id));
        if (entity == null) throw QmsException.notFound(); return entity;
    }
    private SpecificationRevisionEntity latest(String specificationId) {
        return revisions.selectOne(new LambdaQueryWrapper<SpecificationRevisionEntity>()
                .eq(SpecificationRevisionEntity::getSpecificationId, specificationId)
                .orderByDesc(SpecificationRevisionEntity::getRevisionNo).last("LIMIT 1"));
    }
    private List<SpecificationItemEntity> loadItems(String revisionId) {
        return items.selectList(new LambdaQueryWrapper<SpecificationItemEntity>()
                .eq(SpecificationItemEntity::getRevisionId, revisionId)
                .orderByAsc(SpecificationItemEntity::getSequenceNo));
    }
    private QualityModels.DetailView detail(QualitySpecificationEntity specification, SpecificationRevisionEntity revision) {
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
    private static void requireStatus(SpecificationRevisionEntity entity, String expected) {
        if (!expected.equals(entity.getStatus())) throw QmsException.immutable();
    }
    private static String decimal(java.math.BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private static QualityModels.CharacteristicView characteristic(InspectionCharacteristicEntity entity) {
        return new QualityModels.CharacteristicView(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDefaultUomId(), entity.getDefaultUomVersion(), Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion());
    }
    private static QualityModels.MethodView method(InspectionMethodEntity entity) {
        return new QualityModels.MethodView(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion());
    }
}
