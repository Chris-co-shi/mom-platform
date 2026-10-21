package io.github.chrisshi.mom.mdm.application;

import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.data.page.PageAdapter;
import io.github.chrisshi.mom.mdm.api.ManufacturingResourceReference;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.RoutingPageParams;
import io.github.chrisshi.mom.mdm.application.model.RoutingModels;
import io.github.chrisshi.mom.mdm.infrastructure.entity.ProductionLineEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.RoutingEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.RoutingRevisionEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.RoutingStepEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WorkstationEntity;
import io.github.chrisshi.mom.mdm.infrastructure.entity.WorkshopEntity;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.PlantMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.ProductionLineMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.RoutingMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.RoutingRevisionMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.RoutingStepMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.WorkshopMapper;
import io.github.chrisshi.mom.mdm.infrastructure.mapper.WorkstationMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static io.github.chrisshi.mom.mdm.application.MdmMasterDataRules.requireVersion;

/**
 * 工艺路线和不可变发布修订的 MDM 本地事务用例。
 *
 * <p>步骤直接引用 ProductionLine 或 Workstation，不建立 WorkCenter 同义模型。引用校验按
 * Plant→Workshop→ProductionLine→Workstation 稳定顺序锁行；停用仅阻止新发布或新引用，
 * 不回写已发布历史定义。</p>
 */
@Component
@RequiredArgsConstructor
public class RoutingApplication {
    private static final String DRAFT = "DRAFT";
    private static final String RELEASED = "RELEASED";
    private static final String OBSOLETE = "OBSOLETE";

    private final RoutingMapper routingMapper;
    private final RoutingRevisionMapper revisionMapper;
    private final RoutingStepMapper stepMapper;
    private final PlantMapper plantMapper;
    private final WorkshopMapper workshopMapper;
    private final ProductionLineMapper lineMapper;
    private final WorkstationMapper stationMapper;
    private final FactoryStructureApplication factoryApplication;
    private final PageAdapter pageAdapter;

    

    /** 原子创建路线身份、首个草稿修订和有序工序。 */
    @Transactional
    public RoutingModels.DetailView create(String code, String name, String plantId,
                                           List<RoutingModels.StepInput> steps) {
        RoutingEntity routing = new RoutingEntity();
        routing.setCode(MdmMasterDataRules.code(code));
        routing.setName(MdmMasterDataRules.name(name));
        routing.setPlantId(MdmMasterDataRules.id(plantId, "plantId"));
        List<RoutingModels.StepInput> validated = validateSteps(steps);
        lockAndValidateResources(routing, validated);
        try { routingMapper.insert(routing); }
        catch (DuplicateKeyException exception) { throw MdmException.codeConflict("Routing"); }
        RoutingRevisionEntity revision = newRevision(routing.getId(), 1);
        revisionMapper.insert(revision);
        insertSteps(revision.getId(), validated);
        return detail(routing, revision);
    }

    /** 仅草稿可在技术锁版本匹配时整体替换工序。 */
    @Transactional
    public RoutingModels.DetailView updateDraft(String revisionId, Long lockVersion,
                                                List<RoutingModels.StepInput> steps) {
        RoutingRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireDraft(revision);
        requireVersion(revision.getLockVersion(), lockVersion);
        RoutingEntity routing = requireRouting(revision.getRoutingId());
        List<RoutingModels.StepInput> validated = validateSteps(steps);
        lockAndValidateResources(routing, validated);
        stepMapper.delete(new LambdaQueryWrapper<RoutingStepEntity>()
                .eq(RoutingStepEntity::getRevisionId, revision.getId()));
        insertSteps(revision.getId(), validated);
        requireUpdated(revisionMapper.updateById(revision), revision.getId());
        return detail(routing, revision);
    }

    /** 主行锁串行分配下一个业务修订号；已发布历史步骤复制到新草稿。 */
    @Transactional
    public RoutingModels.DetailView createNextRevision(String routingId) {
        RoutingEntity routing = routingMapper.selectByIdForUpdate(MdmMasterDataRules.id(routingId, "routingId"));
        if (routing == null) throw MdmException.notFound("Routing");
        RoutingRevisionEntity latest = latestRevision(routing.getId());
        if (latest == null) throw MdmException.invalidReference();
        if (DRAFT.equals(latest.getStatus())) throw MdmException.immutable();
        List<RoutingModels.StepInput> steps = stepInputs(latest.getId());
        lockAndValidateResources(routing, steps);
        RoutingRevisionEntity next = newRevision(routing.getId(), latest.getRevisionNo() + 1);
        revisionMapper.insert(next);
        insertSteps(next.getId(), steps);
        return detail(routing, next);
    }

    /** 发布前重新锁定并校验工厂、父链和步骤资源；发布后步骤不可修改。 */
    @Transactional
    public RoutingModels.DetailView release(String revisionId, Long lockVersion) {
        RoutingRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireDraft(revision);
        requireVersion(revision.getLockVersion(), lockVersion);
        RoutingEntity routing = requireRouting(revision.getRoutingId());
        List<RoutingModels.StepInput> steps = stepInputs(revision.getId());
        if (steps.isEmpty()) throw MdmException.invalidReference();
        lockAndValidateResources(routing, steps);
        revision.setStatus(RELEASED);
        revision.setReleasedAt(Instant.now());
        requireUpdated(revisionMapper.updateById(revision), revision.getId());
        return detail(routing, revision);
    }

    /** 废止已发布修订；保留可按 ID 读取的历史定义。 */
    @Transactional
    public RoutingModels.DetailView obsolete(String revisionId, Long lockVersion) {
        RoutingRevisionEntity revision = requireRevisionForUpdate(revisionId);
        requireVersion(revision.getLockVersion(), lockVersion);
        if (!RELEASED.equals(revision.getStatus())) throw MdmException.immutable();
        revision.setStatus(OBSOLETE);
        revision.setObsoleteAt(Instant.now());
        requireUpdated(revisionMapper.updateById(revision), revision.getId());
        return detail(requireRouting(revision.getRoutingId()), revision);
    }

    /** 查询路线最新业务修订，不用技术锁版本代替修订号。 */
    @Transactional(readOnly = true)
    public RoutingModels.DetailView get(String id) {
        RoutingEntity routing = requireRouting(id);
        RoutingRevisionEntity revision = latestRevision(routing.getId());
        if (revision == null) throw MdmException.invalidReference();
        return detail(routing, revision);
    }

    /** 读取指定历史修订与其不可变步骤。 */
    @Transactional(readOnly = true)
    public RoutingModels.DetailView getRevision(String id) {
        RoutingRevisionEntity revision = requireRevision(id);
        return detail(requireRouting(revision.getRoutingId()), revision);
    }

    /** 工厂过滤后按 code/id 稳定分页。 */
    @Transactional(readOnly = true)
    public PageResult<RoutingModels.RoutingView> search(PageQuery<RoutingPageParams> query) {
        RoutingPageParams params = query.params();
        Page<RoutingEntity> page = pageAdapter.toPage(query);
        LambdaQueryWrapper<RoutingEntity> conditions = new LambdaQueryWrapper<>();
        if (params.plantId() != null && !params.plantId().isBlank()) {
            conditions.eq(RoutingEntity::getPlantId, MdmMasterDataRules.id(params.plantId(), "plantId"));
        }
        conditions.orderByAsc(RoutingEntity::getCode).orderByAsc(RoutingEntity::getId);
        routingMapper.selectPage(page, conditions);
        return pageAdapter.toResult(page, RoutingApplication::routingView);
    }

    private void lockAndValidateResources(RoutingEntity routing, List<RoutingModels.StepInput> steps) {
        if (plantMapper.selectByIdForUpdate(routing.getPlantId()) == null) throw MdmException.invalidReference();
        ManufacturingResourceReference plant = factoryApplication.getManufacturingResourceReference(
                "PLANT", routing.getPlantId());
        if (!plant.availableForNewUse()) throw MdmException.invalidReference();

        Set<String> workshopIds = new TreeSet<>();
        Set<String> lineIds = new TreeSet<>();
        Set<String> stationIds = new TreeSet<>();
        for (RoutingModels.StepInput step : steps) {
            if ("PRODUCTION_LINE".equals(step.resourceType())) lineIds.add(step.resourceId());
            else if ("WORKSTATION".equals(step.resourceType())) stationIds.add(step.resourceId());
            else throw MdmException.invalidReference();
        }
        for (String id : stationIds) {
            WorkstationEntity station = stationMapper.selectById(id);
            if (station == null) throw MdmException.invalidReference();
            lineIds.add(station.getProductionLineId());
        }
        for (String id : lineIds) {
            ProductionLineEntity line = lineMapper.selectById(id);
            if (line == null) throw MdmException.invalidReference();
            workshopIds.add(line.getWorkshopId());
        }
        for (String id : workshopIds) {
            WorkshopEntity workshop = workshopMapper.selectByIdForUpdate(id);
            if (workshop == null) throw MdmException.invalidReference();
            if (!routing.getPlantId().equals(workshop.getPlantId())) throw MdmException.invalidReference();
        }
        for (String id : lineIds) if (lineMapper.selectByIdForUpdate(id) == null) {
            throw MdmException.invalidReference();
        }
        for (String id : stationIds) if (stationMapper.selectByIdForUpdate(id) == null) {
            throw MdmException.invalidReference();
        }
        for (RoutingModels.StepInput step : steps) {
            ManufacturingResourceReference resource = factoryApplication.getManufacturingResourceReference(
                    step.resourceType(), step.resourceId());
            if (!resource.availableForNewUse() || !routing.getPlantId().equals(resource.plantId())) {
                throw MdmException.invalidReference();
            }
        }
    }

    private static List<RoutingModels.StepInput> validateSteps(List<RoutingModels.StepInput> steps) {
        if (steps == null || steps.isEmpty() || steps.size() > 500) throw MdmException.invalidReference();
        Set<Integer> sequence = new HashSet<>();
        return steps.stream().map(step -> {
            if (step == null || step.sequenceNo() == null || step.sequenceNo() <= 0
                    || !sequence.add(step.sequenceNo())) throw MdmException.invalidReference();
            String type = step.resourceType();
            if (!"PRODUCTION_LINE".equals(type) && !"WORKSTATION".equals(type)) {
                throw MdmException.invalidReference();
            }
            return new RoutingModels.StepInput(step.sequenceNo(),
                    MdmMasterDataRules.code(step.operationCode()), MdmMasterDataRules.name(step.name()),
                    type, MdmMasterDataRules.id(step.resourceId(), "resourceId"));
        }).sorted(java.util.Comparator.comparing(RoutingModels.StepInput::sequenceNo)).toList();
    }

    private static RoutingRevisionEntity newRevision(String routingId, int number) {
        RoutingRevisionEntity revision = new RoutingRevisionEntity();
        revision.setRoutingId(routingId); revision.setRevisionNo(number); revision.setStatus(DRAFT);
        return revision;
    }

    private void insertSteps(String revisionId, List<RoutingModels.StepInput> steps) {
        for (RoutingModels.StepInput input : steps) {
            RoutingStepEntity step = new RoutingStepEntity();
            step.setRevisionId(revisionId); step.setSequenceNo(input.sequenceNo());
            step.setOperationCode(input.operationCode()); step.setName(input.name());
            step.setResourceType(input.resourceType()); step.setResourceId(input.resourceId());
            stepMapper.insert(step);
        }
    }

    private List<RoutingStepEntity> loadSteps(String revisionId) {
        return stepMapper.selectList(new LambdaQueryWrapper<RoutingStepEntity>()
                .eq(RoutingStepEntity::getRevisionId, revisionId)
                .orderByAsc(RoutingStepEntity::getSequenceNo));
    }

    private List<RoutingModels.StepInput> stepInputs(String revisionId) {
        return loadSteps(revisionId).stream().map(step -> new RoutingModels.StepInput(step.getSequenceNo(),
                step.getOperationCode(), step.getName(), step.getResourceType(), step.getResourceId())).toList();
    }

    private RoutingRevisionEntity latestRevision(String routingId) {
        return revisionMapper.selectOne(new LambdaQueryWrapper<RoutingRevisionEntity>()
                .eq(RoutingRevisionEntity::getRoutingId, routingId)
                .orderByDesc(RoutingRevisionEntity::getRevisionNo).last("LIMIT 1"));
    }

    private RoutingEntity requireRouting(String id) {
        RoutingEntity routing = routingMapper.selectById(MdmMasterDataRules.id(id, "routingId"));
        if (routing == null) throw MdmException.notFound("Routing");
        return routing;
    }

    private RoutingRevisionEntity requireRevision(String id) {
        RoutingRevisionEntity revision = revisionMapper.selectById(MdmMasterDataRules.id(id, "revisionId"));
        if (revision == null) throw MdmException.notFound("RoutingRevision");
        return revision;
    }

    private RoutingRevisionEntity requireRevisionForUpdate(String id) {
        RoutingRevisionEntity revision = revisionMapper.selectByIdForUpdate(MdmMasterDataRules.id(id, "revisionId"));
        if (revision == null) throw MdmException.notFound("RoutingRevision");
        return revision;
    }

    private static void requireDraft(RoutingRevisionEntity revision) {
        if (!DRAFT.equals(revision.getStatus())) throw MdmException.immutable();
    }

    private void requireUpdated(int affected, String id) {
        if (affected == 1) return;
        if (revisionMapper.selectById(id) == null) throw MdmException.notFound("RoutingRevision");
        throw MdmException.versionConflict();
    }

    private RoutingModels.DetailView detail(RoutingEntity routing, RoutingRevisionEntity revision) {
        List<RoutingModels.StepView> steps = loadSteps(revision.getId()).stream()
                .map(step -> new RoutingModels.StepView(step.getId(), step.getSequenceNo(),
                        step.getOperationCode(), step.getName(), step.getResourceType(), step.getResourceId()))
                .toList();
        return new RoutingModels.DetailView(routingView(routing), revisionView(revision), steps);
    }

    private static RoutingModels.RoutingView routingView(RoutingEntity routing) {
        return new RoutingModels.RoutingView(routing.getId(), routing.getCode(), routing.getName(),
                routing.getPlantId(), routing.getCreatedAt(), routing.getUpdatedAt());
    }

    private static RoutingModels.RevisionView revisionView(RoutingRevisionEntity revision) {
        return new RoutingModels.RevisionView(revision.getId(), revision.getRoutingId(),
                revision.getRevisionNo(), revision.getStatus(), revision.getLockVersion(),
                revision.getReleasedAt(), revision.getObsoleteAt());
    }
}
