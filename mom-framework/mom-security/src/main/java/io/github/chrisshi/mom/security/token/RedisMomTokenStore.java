package io.github.chrisshi.mom.security.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * @author 史偕成
 * @date 2026/09/03 09:43
 **/
@RequiredArgsConstructor
public class RedisMomTokenStore implements MomTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private static final String TOKEN_KEY_PREFIX = "mom:token:";

    private String key(String token) {
        return TOKEN_KEY_PREFIX + MomTokenFingerprint.of(token);
    }

    @Override
    public void store(String token, MomTokenPrincipal principal) {
        MomTokenFingerprint.of(token);
        Objects.requireNonNull(principal, "principal");
        Duration ttl = Duration.between(
            clock.instant(),
            principal.expiresAt()
        );
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Token 已过期");
        }
        String json = jsonMapper.writeValueAsString(principal);
        redisTemplate.opsForValue().set(
            key(token),
            json,
            ttl
        );
    }

    @Override
    public Optional<MomTokenPrincipal> find(String token) {
        MomTokenFingerprint.of(token);
        String json = redisTemplate.opsForValue().get(key(token));
        if (json == null) {
            return Optional.empty();
        }
        MomTokenPrincipal momTokenPrincipal = jsonMapper.readValue(json, MomTokenPrincipal.class);
        return Optional.of(momTokenPrincipal);
    }

    @Override
    public void remove(String token) {
        MomTokenFingerprint.of(token);
        redisTemplate.delete(key(token));
    }
}
