/**
 * System Platform 的跨模块稳定契约边界。
 *
 * <p>S13 参数与 S14 字典只暴露跨服务读取所需的稳定枚举和 DTO，不包含数据库 ID、Web Request、Entity、
 * Mapper 或 Repository。Client 尚无真实调用方，契约本身不代表已提供 Feign 适配器。</p>
 */
package io.github.chrisshi.mom.system.api;
