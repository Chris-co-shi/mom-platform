package io.github.chrisshi.mom.auth.controller.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 账号、角色或 Permission 显式启停用请求。
 *
 * <p>该对象只承载 HTTP 边界上的乐观锁版本，目标状态由 enable/disable 路径本身表达，
 * 避免通过任意布尔值隐含状态迁移。事务、引用保护和 Token 快照语义由 Application 负责。</p>
 *
 * @param version 客户端最近读取到的乐观锁版本
 */
public record ChangeStatusRequest(
    @NotNull @PositiveOrZero Long version
) {
}
