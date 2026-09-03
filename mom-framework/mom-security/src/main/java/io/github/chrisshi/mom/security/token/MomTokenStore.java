package io.github.chrisshi.mom.security.token;

import java.util.Optional;

/**
 * MOM Token 存储接口
 *
 * @author 史偕成
 * @date 2026/09/03 09:19
 **/
public interface MomTokenStore {

    /**
     * 存储 Token
     *
     * @param token
     * @param principal
     */
    void store(String token, MomTokenPrincipal principal);

    /**
     * 查询 Token
     *
     * @param token
     * @return
     */
    Optional<MomTokenPrincipal> find(String token);

    /**
     * 删除 Token
     *
     * @param token
     */
    void remove(String token);
}
