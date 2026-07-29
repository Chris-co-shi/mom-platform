/**
 * System Platform 的同步调用适配器边界。
 *
 * <p>S12 不创建真实 Feign Client。本包未来只能依赖 System API 与统一调用基础设施，不得依赖 System
 * Server、其他领域 Server、持久化、缓存、消息或事务实现。</p>
 */
package io.github.chrisshi.mom.system.client;
