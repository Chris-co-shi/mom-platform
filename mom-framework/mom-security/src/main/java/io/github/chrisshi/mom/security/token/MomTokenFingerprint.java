package io.github.chrisshi.mom.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author 史偕成
 * @date 2026/09/03 10:24
 **/
public final class MomTokenFingerprint {

    private MomTokenFingerprint() {
    }

    public static String of(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
