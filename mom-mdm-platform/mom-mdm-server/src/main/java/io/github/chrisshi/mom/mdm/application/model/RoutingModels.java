package io.github.chrisshi.mom.mdm.application.model;

import java.time.Instant;
import java.util.List;

/** Routing 用例输入输出；业务修订号与技术锁版本分开暴露。 */
public final class RoutingModels {
    private RoutingModels() { }

    /** 草稿的有序工序，资源类型仅允许产线或工位。 */
    public record StepInput(Integer sequenceNo, String operationCode, String name,
                            String resourceType, String resourceId) { }

    /** 可复用路线身份；Plant 固定且不包含 MES 执行事实。 */
    public record RoutingView(String id, String code, String name, String plantId,
                              Instant createdAt, Instant updatedAt) { }

    /** 业务生命周期和技术锁版本的只读视图。 */
    public record RevisionView(String id, String routingId, Integer revisionNo, String status,
                               Long lockVersion, Instant releasedAt, Instant obsoleteAt) { }

    /** 发布后不可改写的单条工序。 */
    public record StepView(String id, Integer sequenceNo, String operationCode, String name,
                           String resourceType, String resourceId) { }

    /** 路线身份、一个具体修订与有序步骤的完整定义。 */
    public record DetailView(RoutingView routing, RevisionView revision, List<StepView> steps) { }
}
