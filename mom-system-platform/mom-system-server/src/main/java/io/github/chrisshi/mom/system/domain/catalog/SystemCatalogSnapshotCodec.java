package io.github.chrisshi.mom.system.domain.catalog;

/**
 * Catalog Snapshot 与 JSONB String 的框架无关转换端口。
 *
 * <p>Application 只依赖该端口，不感知 Jackson 或 PostgreSQL TypeHandler。编码必须确定性；解码损坏时必须
 * Fail Closed，不能返回部分目录或空目录伪成功。</p>
 */
public interface SystemCatalogSnapshotCodec {
    /** 编码完整 Snapshot。 */
    String encode(SystemCatalogSnapshot snapshot);

    /** 解码完整 Snapshot。 */
    SystemCatalogSnapshot decode(String json);
}
