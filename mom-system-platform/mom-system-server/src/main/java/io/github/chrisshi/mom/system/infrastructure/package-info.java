/**
 * System 基础设施适配边界。
 *
 * <p>Infrastructure 未来只能实现 Domain/Application Port 并转换底层失败，不能成为业务入口，也不能
 * 访问 IAM Repository、Schema 或内部实现。S13/S14 仅实现 mom_system 参数与字典持久化，不含缓存、
 * 消息或远程业务适配器。</p>
 */
package io.github.chrisshi.mom.system.infrastructure;
