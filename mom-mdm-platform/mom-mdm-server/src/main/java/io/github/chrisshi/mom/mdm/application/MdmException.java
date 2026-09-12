package io.github.chrisshi.mom.mdm.application;

/**
 * MDM 主数据用例向 HTTP 边界暴露的稳定业务异常。
 *
 * <p>该异常属于 Application，不包含 SQL、约束名或连接信息。运行时异常会使本地事务回滚；Controller
 * 将不存在映射为 404、非法输入映射为 400、唯一性和乐观锁冲突映射为 409。</p>
 */
public final class MdmException extends RuntimeException {
    private final String code;
    private final Kind kind;

    /** 异常类别只服务协议映射，不代表主数据生命周期状态。 */
    public enum Kind { BAD_REQUEST, NOT_FOUND, CONFLICT }

    /**
     * @param kind HTTP 适配所需异常类别
     * @param code 稳定机器错误码
     * @param message 脱敏用户可读说明
     */
    public MdmException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    /** @return 稳定机器错误码 */
    public String code() {
        return code;
    }

    /** @return HTTP 边界使用的异常类别 */
    public Kind kind() {
        return kind;
    }

    /** 创建资源不存在异常。 */
    public static MdmException notFound(String resourceName) {
        return new MdmException(Kind.NOT_FOUND, "mdm.resource_not_found", resourceName + "不存在");
    }

    /** 创建业务编码唯一冲突异常。 */
    public static MdmException codeConflict(String resourceName) {
        return new MdmException(Kind.CONFLICT, "mdm.code_conflict", resourceName + "编码已存在");
    }

    /** 创建乐观锁冲突异常。 */
    public static MdmException versionConflict() {
        return new MdmException(Kind.CONFLICT, "mdm.version_conflict", "主数据已被其他请求修改");
    }

    /** 创建父级或引用主数据已停用的冲突异常。 */
    public static MdmException parentDisabled(String resourceName) {
        return new MdmException(Kind.CONFLICT, "mdm.parent_disabled", resourceName + "已停用");
    }

    /** 创建引用关系非法异常。 */
    public static MdmException invalidReference(String message) {
        return new MdmException(Kind.BAD_REQUEST, "mdm.invalid_reference", message);
    }
}
