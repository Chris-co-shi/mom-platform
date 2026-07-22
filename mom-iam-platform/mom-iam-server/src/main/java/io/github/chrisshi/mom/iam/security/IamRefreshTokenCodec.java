package io.github.chrisshi.mom.iam.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** 生成不可恢复的高熵 Opaque Refresh Token，并只计算可持久化 HMAC-SHA-256 摘要。 */
public final class IamRefreshTokenCodec {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecureRandom secureRandom;
    private final byte[] pepper;
    private final int tokenBytes;

    public IamRefreshTokenCodec(IamSessionProperties properties) {
        this(new SecureRandom(), properties.getHmacPepper(), properties.getRefreshTokenBytes());
    }

    IamRefreshTokenCodec(SecureRandom secureRandom, String pepper, int tokenBytes) {
        this.secureRandom = secureRandom;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.tokenBytes = tokenBytes;
    }

    /** @return 只返回给 Client 一次的随机 Opaque Token 明文 */
    public String generate() {
        byte[] random = new byte[tokenBytes];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    /** @return 小写十六进制 HMAC-SHA-256 摘要；不得反向恢复 Token */
    public String digest(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Refresh Token 不能为空");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(token.getBytes(StandardCharsets.US_ASCII)));
        }
        catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法计算 Refresh Token HMAC", exception);
        }
    }
}
