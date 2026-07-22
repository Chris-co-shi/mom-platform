package io.github.chrisshi.mom.security.token;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 将 MOM JWT 中的 Role 与 Permission 映射为 Spring Security Authority。
 *
 * <p>Role 使用标准 {@code ROLE_} 前缀；Permission 保留 {@code domain:resource:action} 原值，避免把
 * OAuth Scope 与业务 Permission 混为一谈。</p>
 */
public final class MomJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        MomSecurityClaims.stringSetClaim(source, MomSecurityClaims.ROLES).stream()
                .sorted()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);
        MomSecurityClaims.stringSetClaim(source, MomSecurityClaims.PERMISSIONS).stream()
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        authorities.sort(Comparator.comparing(GrantedAuthority::getAuthority));
        return List.copyOf(authorities);
    }
}
