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
 * Spring Security 用户与 authority 加载适配器。
 *
 * <p>该类只负责把 MOM 的 User + Role/Permission 查询结果组装为 {@link AuthUserPrincipal}；
 * 密码比对、enabled 检查、凭据清理以及认证失败语义仍由 DaoAuthenticationProvider/ProviderManager 负责。</p>
 *
 * <p>用户单表读取使用 UserMapper，跨 User-Role-Permission 的 authority 聚合使用专用 QueryMapper，
 * 不把多表 JOIN 塞回单表 Mapper。</p>
 */
@Component
public final class AuthUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final AuthenticationQueryMapper authenticationQueryMapper;

    public AuthUserDetailsService(UserMapper userMapper, AuthenticationQueryMapper authenticationQueryMapper) {
        this.userMapper = userMapper;
        this.authenticationQueryMapper = authenticationQueryMapper;
    }

    /**
     * 按规范化后的用户名加载 Spring Security Principal。
     *
     * <p>用户不存在时抛出 {@link UsernameNotFoundException}；DaoAuthenticationProvider 默认会将其隐藏为
     * BadCredentials，避免通过登录接口泄露账号是否存在。</p>
     *
     * @param username 已由 AuthenticationApplication 规范化的登录名
     * @return 包含密码摘要、enabled 状态和 authority 的 AuthUserPrincipal
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userMapper.selectOne(
            new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username)
        );
        if (user == null) {
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
