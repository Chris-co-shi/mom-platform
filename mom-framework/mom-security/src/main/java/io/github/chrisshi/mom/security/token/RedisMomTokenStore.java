package io.github.chrisshi.mom.security.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * @author 史偕成
 * @date 2026/09/03 09:43
 **/
@Component
@RequiredArgsConstructor
public class RedisMomTokenStore implements MomTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Override
    public void store(String token, MomTokenPrincipal principal) {
        requireToken(token);
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
            token,
            json,
            ttl
        );
    }

    @Override
    public Optional<MomTokenPrincipal> find(String token) {
        requireToken(token);
        String json = redisTemplate.opsForValue().get(token);
        if (json == null) {
            return Optional.empty();
        }
        MomTokenPrincipal momTokenPrincipal = jsonMapper.readValue(json, MomTokenPrincipal.class);
        return Optional.of(momTokenPrincipal);
    }

    @Override
    public void remove(String token) {
        requireToken(token);
        redisTemplate.delete(key(token));
    }
}
