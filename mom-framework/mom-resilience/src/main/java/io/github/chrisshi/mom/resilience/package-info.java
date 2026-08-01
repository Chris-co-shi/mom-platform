/**
 * MOM Resilience 治理基础。
 *
 * <p>模块复用 Spring Cloud CircuitBreaker 与 Resilience4j 官方能力，冻结配置命名和事务外执行不变量，
 * 不包装数据库/Redis/Nacos，也不创建没有真实消费者的自定义 Strategy/Registry。Retry 必须由调用方证明
 * 幂等并显式开启；Fallback 通过 Spring Cloud 的失败契约向业务 Adapter 抛出，不返回伪造成功。</p>
 */
package io.github.chrisshi.mom.resilience;
