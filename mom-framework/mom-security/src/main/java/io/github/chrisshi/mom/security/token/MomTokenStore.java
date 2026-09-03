package io.github.chrisshi.mom.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * MOM 令牌持久化存储契约。
 *
 * <p>定义令牌的写入、查询和删除操作，实现方可以是 Redis、数据库或其他存储后端。
 * 本接口不关心令牌的签发与校验逻辑，仅负责认证快照的存取。</p>
 *
 * <p>实现方必须保证线程安全。存储基础设施不可用时，
 * 允许向调用方抛出运行时异常由上层统一处理。</p>
 *
 * @author 史偕成
 * @since 2026-09-03
 */
public interface MomTokenStore {

    String TOKEN_KEY_PREFIX = "mom:token:";

    /**
     * 存储令牌与对应的认证快照。
     *
     * <p>实现方应根据令牌的剩余有效期设置合理的 TTL，
     * 避免过期令牌长期占用存储空间。</p>
     *
     * @param token     已签发的原始令牌字符串，不允许为 {@code null} 或空白
     * @param principal 令牌关联的认证快照，不允许为 {@code null}
     */
    void store(String token, MomTokenPrincipal principal);

    /**
     * 根据原始令牌查询认证快照。
     *
     * @param token 已签发的原始令牌字符串，不允许为 {@code null} 或空白
     * @return 令牌存在时返回对应的认证快照；令牌不存在或已过期时返回 {@link Optional#empty()}
     * @throws RuntimeException 存储基础设施访问失败时向上传播
     */
    Optional<MomTokenPrincipal> find(String token);

    /**
     * 删除指定令牌的认证快照。
     *
     * <p>用于主动注销令牌（如用户登出、强制下线）。
     * 令牌不存在时实现方应静默忽略，不抛出异常。</p>
     *
     * @param token 待删除的原始令牌字符串，不允许为 {@code null} 或空白
     */
    void remove(String token);

//    /**
//     * 校验令牌格式合法性，拒绝 {@code null} 或空白令牌。
//     *
//     * <p>作为默认方法供各实现在公开方法入口处统一前置校验，
//     * 避免无效参数穿透到存储层。</p>
//     *
//     * @param token 待校验的原始令牌字符串
//     * @throws IllegalArgumentException 当 {@code token} 为 {@code null} 或空白时抛出
//     */
//    default void requireToken(String token) {
//        if (token == null || token.isBlank()) {
//            throw new IllegalArgumentException("token 不能为空");
//        }
//    }
//
//    /**
//     * 根据原始令牌生成存储键。
//     *
//     * <p>将令牌经 SHA-256 哈希后拼接统一命名空间前缀，
//     * 避免在存储后端中暴露原始令牌明文，同时保证键长度固定。</p>
//     *
//     * @param token 原始令牌字符串
//     * @return 格式为 {@code mom:token:<sha256-hex>} 的存储键
//     */
//    default String key(String token) {
//        return TOKEN_KEY_PREFIX + sha256(token);
//    }
//
//    /**
//     * 计算字符串的 SHA-256 摘要并以十六进制小写字符串返回。
//     *
//     * @param value 待摘要的原始字符串
//     * @return 64 位十六进制 SHA-256 摘要字符串
//     * @throws IllegalStateException 当运行环境不提供 SHA-256 算法时抛出（理论上不会发生）
//     */
//    default String sha256(String value) {
//        try {
//            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
//            byte[] hash = messageDigest.digest(
//                value.getBytes(StandardCharsets.UTF_8)
//            );
//            return HexFormat.of().formatHex(hash);
//        } catch (NoSuchAlgorithmException e) {
//            throw new IllegalStateException(
//                "SHA-256 algorithm unavailable",
//                e
//            );
//        }
//    }
}
