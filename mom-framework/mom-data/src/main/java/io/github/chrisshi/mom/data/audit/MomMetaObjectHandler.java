package io.github.chrisshi.mom.data.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import io.github.chrisshi.mom.core.security.AuditActor;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.data.config.MomDataAuditProperties;
import org.apache.ibatis.reflection.MetaObject;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * MOM MyBatis-Plus 服务端审计字段强制填充器。
 *
 * <p>INSERT 强制覆盖 created/updated 时间与 Actor，UPDATE 只覆盖最近修改字段。同一次 INSERT 复用一个
 * UTC Instant 和一个 Actor。处理器不填充 Factory、Party、Session、版本、逻辑删除或业务状态字段。</p>
 */
public final class MomMetaObjectHandler implements MetaObjectHandler {

    private final Clock clock;
    private final CurrentActorProvider actorProvider;
    private final MomDataAuditProperties properties;

    public MomMetaObjectHandler(Clock clock, CurrentActorProvider actorProvider, MomDataAuditProperties properties) {
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
        this.actorProvider = Objects.requireNonNull(actorProvider, "actorProvider 不能为空");
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
    }

    /** INSERT 时以服务端 Actor 和同一 UTC 时间强制覆盖四个审计字段。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        Objects.requireNonNull(metaObject, "metaObject 不能为空");
        Instant now = clock.instant();
        String actorId = resolveActorId();
        overwrite(metaObject, "createdAt", now);
        overwrite(metaObject, "createdBy", actorId);
        overwrite(metaObject, "updatedAt", now);
        overwrite(metaObject, "updatedBy", actorId);
    }

    /** UPDATE 时只覆盖最近修改时间和操作人，不修改创建审计字段。 */
    @Override
    public void updateFill(MetaObject metaObject) {
        Objects.requireNonNull(metaObject, "metaObject 不能为空");
        overwrite(metaObject, "updatedAt", clock.instant());
        overwrite(metaObject, "updatedBy", resolveActorId());
    }

    private String resolveActorId() {
        if (properties.isFailOnMissingActor()) {
            return actorProvider.requireCurrentActor().actorId();
        }
        return actorProvider.findCurrentActor().map(AuditActor::actorId).orElse(null);
    }

    private static void overwrite(MetaObject metaObject, String property, Object value) {
        if (metaObject.hasSetter(property)) {
            metaObject.setValue(property, value);
        }
    }
}
