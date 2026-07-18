package io.github.chrisshi.mom.mdm.application;

import io.github.chrisshi.mom.integration.api.seata.IntegrationSeataAtParticipantRequest;
import io.github.chrisshi.mom.integration.api.seata.IntegrationSeataAtParticipantResponse;
import io.github.chrisshi.mom.integration.client.IntegrationSeataAtParticipantClient;
import io.github.chrisshi.mom.mdm.interfaces.rest.internal.MdmSeataAtProbeRequest;
import io.github.chrisshi.mom.mdm.interfaces.rest.internal.MdmSeataAtProbeResponse;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * MDM 发起的 Seata AT 受控全局事务服务。
 *
 * <p>该服务只验证两个明确数据库资源之间的短同步事务：先提交 MDM 本地分支，再通过 OpenFeign 调用
 * Integration 分支。全局事务超时固定为 10 秒，且方法内不得引入消息等待、重试循环、人工交互、设备执行
 * 或第三方回调。</p>
 *
 * <p>Bean 与技术接口使用同一显式开关注册。Seata 或数据库未启用时，普通 MDM 上下文不会创建全局事务
 * 发起服务；一旦开关打开，缺少本地分支、Feign Client 或 Seata 基础设施会让应用 fail-fast。</p>
 *
 * <p>Spring Cloud Alibaba Seata 负责在 Feign 调用中传播 XID。服务会比较两端观察到的 XID，若不一致则
 * 主动失败并全局回滚。类不能声明为 {@code final}，因为 Seata 和 Spring 需要为注解方法创建代理。</p>
 */
@Service
@ConditionalOnProperty(
        prefix = "mom.mdm.seata-at-probe",
        name = "enabled",
        havingValue = "true")
public class MdmSeataAtProbeService {

    private final MdmSeataAtLocalParticipantService localParticipantService;
    private final IntegrationSeataAtParticipantClient integrationClient;

    /**
     * 创建 MDM AT 全局事务发起服务。
     *
     * @param localParticipantService MDM 当前数据库的短本地分支
     * @param integrationClient Integration 远端 AT 技术参与者 Client
     */
    public MdmSeataAtProbeService(
            MdmSeataAtLocalParticipantService localParticipantService,
            IntegrationSeataAtParticipantClient integrationClient) {
        this.localParticipantService = Objects.requireNonNull(
                localParticipantService,
                "localParticipantService 不能为空");
        this.integrationClient = Objects.requireNonNull(
                integrationClient,
                "integrationClient 不能为空");
    }

    /**
     * 执行一个最多包含两个数据库分支的短 Seata AT 全局事务。
     *
     * @param request 技术事务键、两端写入值和单一故障注入点
     * @return 两端观察到的相同 XID 和提交中状态
     * @throws IllegalArgumentException 请求同时启用两个故障点或关键文本为空时抛出
     * @throws RuntimeException 本地分支、远端分支、XID 校验或主动故障注入失败时抛出并全局回滚
     */
    @GlobalTransactional(
            name = "p01-s06-seata-at-probe",
            timeoutMills = 10_000,
            rollbackFor = Exception.class)
    public MdmSeataAtProbeResponse execute(MdmSeataAtProbeRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        validateFailurePoint(request);

        String transactionKey = requireText(request.transactionKey(), "transactionKey");
        String coordinatorXid = localParticipantService.record(
                transactionKey,
                requireText(request.coordinatorValue(), "coordinatorValue"));

        IntegrationSeataAtParticipantResponse participantResponse = integrationClient.participate(
                new IntegrationSeataAtParticipantRequest(
                        transactionKey,
                        requireText(request.participantValue(), "participantValue"),
                        request.failParticipant()));
        if (!coordinatorXid.equals(participantResponse.xid())) {
            throw new IllegalStateException("MDM 与 Integration 观察到的 Seata XID 不一致");
        }
        if (request.failAfterParticipant()) {
            throw new IllegalStateException("P01-S06 主动触发远端成功后的全局回滚");
        }
        return new MdmSeataAtProbeResponse(
                transactionKey,
                coordinatorXid,
                participantResponse.xid(),
                "COMMITTING");
    }

    private static void validateFailurePoint(MdmSeataAtProbeRequest request) {
        if (request.failParticipant() && request.failAfterParticipant()) {
            throw new IllegalArgumentException("一次技术请求只能启用一个故障注入点");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
