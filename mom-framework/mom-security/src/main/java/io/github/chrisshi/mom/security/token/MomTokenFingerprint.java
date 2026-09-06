package io.github.chrisshi.mom.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 令牌指纹生成器。
 *
 * <p>对原始令牌计算 SHA-256 摘要，用于生成存储键或比对令牌一致性，
 * 避免在存储后端和日志中暴露令牌明文。
 * 本类为不可实例化的工具类。</p>
 *
 * @author 史偕成
 * @since 2026-09-03
 */
public final class MomTokenFingerprint {

    private MomTokenFingerprint() {
    }

    /**
     * 计算原始令牌的 SHA-256 指纹。
     *
     * @param token 原始令牌字符串，不允许为 {@code null} 或空白
     * @return 64 位十六进制小写 SHA-256 摘要字符串
     * @throws IllegalArgumentException 当 {@code token} 为 {@code null} 或空白时抛出
     * @throws IllegalStateException    当运行环境不提供 SHA-256 算法时抛出
     */
    public static String of(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 计算 UTF-8 编码字节摘要并转换为十六进制字符串
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
