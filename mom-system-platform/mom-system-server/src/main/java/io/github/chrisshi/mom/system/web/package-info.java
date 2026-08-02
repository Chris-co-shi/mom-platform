/**
 * System HTTP 入站适配边界。
 *
 * <p>Web 未来只负责协议适配并调用 Application，不得直接访问 Domain 持久化、Mapper、Repository 或
 * Infrastructure。S13 参数与 S14 字典 Controller 只依赖各自 Application 用例并引用 IAM Permission Code。</p>
 */
package io.github.chrisshi.mom.system.web;
