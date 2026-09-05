package io.github.chrisshi.mom.auth.infrastructure.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.chrisshi.mom.auth.infrastructure.entity.UserEntity;
import io.github.chrisshi.mom.auth.infrastructure.mapper.UserMapper;
import io.github.chrisshi.mom.auth.infrastructure.query.AuthenticationQueryMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Security 用户加载适配器。
 *
 * <p>这里只负责把 MOM 用户、角色和 Permission 加载为 {@link UserDetails}；
 * 密码比对、enabled 状态检查以及认证异常语义由 DaoAuthenticationProvider 负责。</p>
 */
@Component
public final class AuthUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final AuthenticationQueryMapper authenticationQueryMapper;

    public AuthUserDetailsService(UserMapper userMapper, AuthenticationQueryMapper authenticationQueryMapper) {
        this.userMapper = userMapper;
        this.authenticationQueryMapper = authenticationQueryMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userMapper.selectOne(
            new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username)
        );
        if (user == null) {
            // DaoAuthenticationProvider 默认会把用户不存在隐藏为 BadCredentials，避免泄露账号存在性。
            throw new UsernameNotFoundException("用户不存在");
        }

        List<String> authorities = authenticationQueryMapper.selectAuthoritiesByUserId(user.getId());
        return new AuthUserPrincipal(
            user.getId(),
            user.getUsername(),
            user.getPasswordHash(),
            Boolean.TRUE.equals(user.getEnabled()),
            authorities
        );
    }
}
