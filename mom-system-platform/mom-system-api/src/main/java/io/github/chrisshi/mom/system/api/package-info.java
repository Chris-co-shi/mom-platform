/**
 * System Platform 的跨模块稳定契约边界。
 *
 * <p>S12 只建立包边界，不定义参数、字典、偏好、应用目录、菜单或其他业务契约。后续只有在存在真实
 * 调用方并由对应 Slice 批准后，才允许在此包中增加与传输、持久化实现无关的契约。</p>
 */
package io.github.chrisshi.mom.system.api;
