/**
 * System HTTP 入站适配边界。
 *
 * <p>Web 未来只负责协议适配并调用 Application，不得直接访问 Domain 持久化、Mapper、Repository 或
 * Infrastructure。S12 当前没有 Controller，也没有公开业务 API。</p>
 */
package io.github.chrisshi.mom.system.web;
