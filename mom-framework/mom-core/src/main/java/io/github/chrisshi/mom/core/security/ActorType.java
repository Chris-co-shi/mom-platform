package io.github.chrisshi.mom.core.security;

/**
 * MOM 数据审计操作人的身份类别。
 *
 * <p>该枚举位于 {@code mom-core}，不依赖 Spring Security、Web 或 ORM。它只表达一次写操作以普通用户、
 * 管理操作或显式系统任务身份执行，不承载角色、权限、工厂或主体范围。</p>
 */
public enum ActorType {

    /** 普通已认证业务用户。 */
    USER,

    /** 由调用方显式建立的管理操作上下文。 */
    ADMIN,

    /** 定时任务、消息消费、Outbox 发布或数据同步等显式系统操作。 */
    SYSTEM
}
