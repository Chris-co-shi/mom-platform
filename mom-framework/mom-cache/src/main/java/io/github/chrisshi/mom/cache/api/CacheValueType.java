package io.github.chrisshi.mom.cache.api;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 绑定稳定缓存类型标识、Schema 版本与 Java 恢复类型。
 *
 * <p>信封只保存稳定 {@link #id()}，不会保存任意 Java FQCN。Java 类型只在当前进程内用于 Jackson 精确恢复，
 * 禁止使用 {@link Object} 触发弱类型 Map 反序列化。该契约同时阻止最终授权决策进入通用 CacheService；
 * 如未来确有安全例外，必须先由 Accepted Security ADR 建立专用边界，而不是放宽这里的默认规则。</p>
 *
 * @param id            跨版本稳定的逻辑类型标识
 * @param schemaVersion Payload Schema 版本，必须为正数
 * @param javaType      精确恢复类型，不能是 Object
 * @param <T>           缓存值类型
 */
public record CacheValueType<T>(String id, int schemaVersion, Class<T> javaType) {

    private static final Pattern TYPE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Set<String> FORBIDDEN_SECURITY_TERMS = Set.of(
            "authorizationdecision",
            "permissionevaluationresult",
            "allowdenydecision"
    );

    public CacheValueType {
        Objects.requireNonNull(id, "缓存值类型标识不能为空");
        Objects.requireNonNull(javaType, "缓存值 Java 类型不能为空");
        if (!TYPE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("缓存值类型标识必须是稳定的小写逻辑名称");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("缓存值 Schema 版本必须为正数");
        }
        if (Object.class.equals(javaType)) {
            throw new IllegalArgumentException("CacheValueType 禁止使用 Object.class");
        }
        String semanticName = normalize(id + javaType.getSimpleName());
        if (FORBIDDEN_SECURITY_TERMS.stream().anyMatch(semanticName::contains)) {
            throw new IllegalArgumentException("最终授权决策禁止进入通用 CacheService");
        }
    }

    /**
     * 创建精确缓存值类型。
     *
     * @param id 稳定逻辑类型，不得使用 Java 类名充当线格式
     * @param schemaVersion Payload Schema 版本
     * @param javaType 当前进程的精确恢复类型
     * @param <T> 缓存值类型
     * @return 已验证、不可变且线程安全的类型描述
     */
    public static <T> CacheValueType<T> of(String id, int schemaVersion, Class<T> javaType) {
        return new CacheValueType<>(id, schemaVersion, javaType);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
