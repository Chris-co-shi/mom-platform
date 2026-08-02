/**
 * System 用例编排边界。
 *
 * <p>Application 未来只编排经批准的 System 用例并依赖 Domain/Port，不得依赖 Web、Mapper、Entity、
 * Repository 实现或其他领域 Server。S13 参数与 S14 字典的公共写方法分别定义本地事务边界。</p>
 */
package io.github.chrisshi.mom.system.application;
