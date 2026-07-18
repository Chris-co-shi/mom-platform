package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

/**
 * MDM PostgreSQL 技术探针创建请求。
 *
 * @param probeKey 稳定且唯一的验证键
 * @param probeValue 需要写入 PostgreSQL 的验证值
 */
public record MdmDataProbeRequest(String probeKey, String probeValue) {
}
