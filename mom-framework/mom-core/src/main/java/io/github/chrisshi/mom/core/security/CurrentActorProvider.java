package io.github.chrisshi.mom.core.security;

import java.util.Optional;

/**
 * 提供当前操作主体标识的跨模块抽象。
 *
 * <p>数据审计、操作日志和消息元数据只能依赖该接口，不能直接依赖 IAM、Spring Security 或某个
 * HTTP Header。这样可以让 Web 请求、定时任务、消息消费者和外部系统接入分别提供自己的主体解析实现，
 * 同时保持 {@code mom-core} 不反向依赖安全模块。</p>
 *
 * <p>主体标识使用字符串而不是固定数值类型，允许表示平台用户、系统任务、外部系统账号或设备身份。
 * 未能可靠确认主体时应返回空值，禁止伪造为某个默认用户。</p>
 */
@FunctionalInterface
public interface CurrentActorProvider {

    /**
     * 获取当前执行上下文中的主体标识。
     *
     * @return 已确认的主体标识；当前上下文没有主体或无法可靠确认时返回空
     */
    Optional<String> currentActorId();
}
