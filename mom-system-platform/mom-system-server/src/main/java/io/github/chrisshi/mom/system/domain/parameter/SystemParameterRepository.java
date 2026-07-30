package io.github.chrisshi.mom.system.domain.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;

import java.util.List;
import java.util.Optional;

/**
 * System Parameter 的领域持久化端口。
 *
 * <p>Application 只依赖该端口，不感知 MyBatis Entity、Mapper 或 SQL。实现必须使用唯一 System
 * DataSource；同 Key 写事务先获取事务级 Key 锁，再完成跨作用域类型检查和写入。数据库不可用时不降级。</p>
 */
public interface SystemParameterRepository {

    /** 在当前本地事务内串行化同 parameterKey 的写入；事务结束自动释放。 */
    void lockParameterKey(String parameterKey);

    /** 按技术主键读取参数。 */
    Optional<SystemParameter> findById(String id);

    /** 按规范作用域与 Key 精确读取参数。 */
    Optional<SystemParameter> findByScopeAndKey(
            ParameterScopeType scopeType, String scopeCode, String parameterKey);

    /** 读取同 Key 的所有 Scope，用于强制类型一致性。 */
    List<SystemParameter> findAllByKey(String parameterKey);

    /** 插入参数并返回数据库填充后的完整快照。 */
    SystemParameter insert(SystemParameter parameter);

    /** 使用实体 Version CAS 更新内容；失败返回 false。 */
    boolean update(SystemParameter parameter);

    /** 使用实体 Version CAS 修改状态；失败返回 false。 */
    boolean updateStatus(SystemParameter parameter);

    /** 按精确条件分页读取。 */
    ParameterPage findPage(ParameterQuery query);

    /** Infrastructure 无关的有限分页查询。 */
    record ParameterQuery(
            ParameterScopeType scopeType,
            String scopeCode,
            String parameterKey,
            Boolean enabled,
            int page,
            int size) {
    }

    /** Infrastructure 无关的分页结果。 */
    record ParameterPage(List<SystemParameter> items, long total, int page, int size) {
        public ParameterPage {
            items = List.copyOf(items);
        }
    }
}
