package io.github.chrisshi.mom.mdm.application;

/**
 * MDM 主数据用例向 HTTP 边界暴露的稳定业务异常。
 *
 * <p>该异常属于 Application，只携带稳定 code、messageKey 与位置参数，不生成最终展示文本。
 * 运行时异常使本地事务回滚；Controller 按请求 Locale 解析文案并映射 HTTP 状态。</p>
 */
public final class MdmException extends RuntimeException {
    private final String code;
    private final Kind kind;
    private final String namespace;
    private final String messageKey;
    private final Object[] args;

    /** 异常类别只服务协议映射，不代表主数据生命周期状态。 */
    public enum Kind { BAD_REQUEST, NOT_FOUND, CONFLICT }

    /**
     * @param kind HTTP 适配所需异常类别
     * @param code 稳定机器错误码
     * @param namespace MDM 自有稳定 namespace
     * @param messageKey namespace 内稳定译文键
     * @param args 数字占位符参数，不得包含 Secret
     */
    public MdmException(Kind kind, String code, String namespace, String messageKey, Object... args) {
        super(namespace + "." + messageKey);
        this.kind = kind;
        this.code = code;
        this.namespace = namespace;
        this.messageKey = messageKey;
        this.args = args == null ? new Object[0] : args.clone();
    }

    /** @return 稳定机器错误码 */
    public String code() {
        return code;
    }

    /** @return HTTP 边界使用的异常类别 */
    public Kind kind() {
        return kind;
    }

    /** @return 当前业务消息所属 namespace */
    public String namespace() {
        return namespace;
    }

    /** @return namespace 内稳定译文键 */
    public String messageKey() {
        return messageKey;
    }

    /** @return 防御性复制的位置参数 */
    public Object[] args() {
        return args.clone();
    }

    /** 创建资源不存在异常。 */
    public static MdmException notFound(String resourceName) {
        return new MdmException(Kind.NOT_FOUND, "mdm.resource_not_found", "mdm", "error.resource_not_found", resourceName);
    }

    /** 创建业务编码唯一冲突异常。 */
    public static MdmException codeConflict(String resourceName) {
        return new MdmException(Kind.CONFLICT, "mdm.code_conflict", "mdm", "error.code_conflict", resourceName);
    }

    /** 创建量纲编码或七维向量唯一性冲突异常，不向调用方暴露数据库约束名。 */
    public static MdmException dimensionConflict() {
        return new MdmException(Kind.CONFLICT, "mdm.dimension_conflict", "mdm", "error.dimension_conflict");
    }

    /** 创建乐观锁冲突异常。 */
    public static MdmException versionConflict() {
        return new MdmException(Kind.CONFLICT, "mdm.version_conflict", "mdm", "error.version_conflict");
    }

    /** 创建父级或引用主数据已停用的冲突异常。 */
    public static MdmException parentDisabled(String resourceName) {
        return new MdmException(Kind.CONFLICT, "mdm.parent_disabled", "mdm", "error.parent_disabled", resourceName);
    }

    /** 创建引用关系非法异常。 */
    public static MdmException invalidReference() {
        return new MdmException(Kind.BAD_REQUEST, "mdm.invalid_reference", "mdm", "error.invalid_reference");
    }

    /** 创建不可变业务身份被普通入口修改的冲突异常。 */
    public static MdmException immutable() {
        return new MdmException(Kind.CONFLICT, "mdm.immutable_master_data", "mdm", "error.immutable_master_data");
    }

    /** 创建仍被非删除业务数据引用的生命周期冲突异常。 */
    public static MdmException resourceReferenced(String resourceName) {
        return new MdmException(Kind.CONFLICT, "mdm.resource_referenced",
                "mdm", "error.resource_referenced", resourceName);
    }

    /** 创建物料分类不是叶子节点的稳定冲突异常。 */
    public static MdmException categoryNotLeaf() {
        return new MdmException(Kind.CONFLICT, "mdm.material_category_not_leaf",
                "mdm", "error.material_category_not_leaf");
    }

    /** 创建分类级联规模超过 V1 单事务保护上限的稳定冲突异常。 */
    public static MdmException categoryCascadeTooLarge(int limit) {
        return new MdmException(Kind.CONFLICT, "mdm.material_category_cascade_too_large",
                "mdm", "error.material_category_cascade_too_large", limit);
    }

    /** 创建单位不在同一换算类别的输入异常。 */
    public static MdmException incompatibleUom() {
        return new MdmException(Kind.BAD_REQUEST, "mdm.incompatible_uom", "mdm", "error.incompatible_uom");
    }

    /** 创建因规则要求精确计算但结果无法精确表示而产生的稳定输入异常。 */
    public static MdmException conversionInexact() {
        return new MdmException(Kind.BAD_REQUEST, "mdm.conversion_inexact", "mdm", "error.conversion_inexact");
    }
}
