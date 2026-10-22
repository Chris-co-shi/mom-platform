# MOM Cache 使用指南

## 1. 适用边界

`mom-cache` 只缓存可从 PostgreSQL 或其他权威来源重建的读投影。业务 Application 依赖自己的 Cache Port，Infrastructure Cache Adapter 才依赖 `CacheService`。缓存命中不改变事务、授权或领域事实。

禁止缓存最终 Authorization Decision、Permission Evaluation Result 或 Allow/Deny 判定。安全例外必须先有 Accepted Security ADR。Revoked Session 等 fail-closed 安全状态不是普通 Cache。

## 2. Region 与类型

业务 bounded context 拥有具体 `CacheRegion<T>` 常量，Framework 不维护业务枚举：

```java
public static final CacheRegion<DictionarySnapshot> DICTIONARY = new CacheRegion<>(
        "system",
        "dictionary",
        1,
        CacheValueType.of("system.dictionary", 1, DictionarySnapshot.class),
        Duration.ofMinutes(1),
        Duration.ofMinutes(10),
        true,
        true
);
```

- `keyVersion` 改变物理 Key Namespace，用于不兼容的 Key/能力演进。
- `schemaVersion` 描述 Payload Schema；不兼容时按 Miss 回源。
- `valueType` 是稳定逻辑 ID，不是 Java FQCN。
- TTL 必须依据数据陈旧容忍度、写后失效传播和回源容量确定，不复制未经验证的默认值。

## 3. Global 与 Factory Scope

```java
CacheEntryKey.of(DICTIONARY, CacheScope.global(), "material-type");
CacheEntryKey.of(LAYOUT, CacheScope.factory(validatedFactoryId), protectedSubject);
```

完整 Key：

```text
mom:{environment}:{scope}:{bounded-context}:cache:v{keyVersion}:{capability}:{subject}
```

- `_global` 只能由 `CacheScope.global()` 产生。
- Factory ID 必须来自服务端已验证的授权和对象归属，禁止直接信任 Header。
- Subject 含敏感信息时先使用项目批准的摘要方案；不得写入 Token、姓名、原始幂等键或其他个人信息。

## 4. 读取与故障策略

```text
L1 Hit → Return
L1 Miss → L2 Hit → Refresh L1 → Return
L1/L2 Miss 或 Redis Failure → Loader/业务 Adapter 回源
```

`getOrLoad` 的 Loader 应读取权威来源。Redis timeout/error 只产生 Cache Miss 和指标；Loader 异常正常抛出。损坏或版本不兼容的 L2 数据会删除精确 Key，不会恢复为 `LinkedHashMap`。

System Runtime 读取还必须先读取 PostgreSQL 权威 Header，再决定当前版本 Cache Key，避免返回旧发布版本。

## 5. 失效

- 单条数据变化使用 typed `evict`，精确删除 L1/L2。
- 版本整体变化可更新 `keyVersion` 并调用 `invalidateLocalRegion`。
- L2 旧版本由 TTL 回收；禁止 Redis `KEYS` 或无界 `SCAN` 批量删除。
- 失效事件重复到达必须幂等。

## 6. Legacy API

`CacheType`、旧 `CacheKey`、`CachePolicy` 和旧 `CacheService` 方法仅为迁移保留。新生产代码不得调用或增加枚举值。删除必须满足 ADR-032 的全仓零调用、连续两个正式 Release 生产指标为零和 Removal ADR。

Prometheus 退出查询：

```promql
increase(mom_cache_legacy_usage_total[release-window]) == 0
```

## 7. 指标

使用 `mom.cache.hit/miss/eviction/error/redis.timeout/legacy.usage`。标签只能是固定 Layer/Operation；禁止把 Region Subject、Factory、用户、业务单号、Trace ID 或完整 URL 作为标签。
