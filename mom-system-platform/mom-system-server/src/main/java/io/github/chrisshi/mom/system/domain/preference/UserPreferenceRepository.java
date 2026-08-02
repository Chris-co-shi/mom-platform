package io.github.chrisshi.mom.system.domain.preference;

import java.util.List;
import java.util.Optional;

/**
 * System 用户偏好持久化 Port。
 *
 * <p>Port 只暴露类型化领域对象，不泄漏 Entity、Mapper、Wrapper、JSON String 或 affected rows。实现使用
 * 单 PostgreSQL 本地事务；数据库不可用时 fail closed，不缓存也不伪造成功。</p>
 */
public interface UserPreferenceRepository {
    Optional<UserPreference> findPreference(String userId);

    UserPreference insertPreference(UserPreference preference);

    boolean updatePreference(UserPreference preference);

    Optional<UserViewSetting> findView(String userId, String applicationCode, String viewKey);

    UserViewSetting insertView(UserViewSetting setting);

    boolean updateView(UserViewSetting setting);

    List<UserViewSetting> findViews(String userId, String applicationCode, int limit);
}
