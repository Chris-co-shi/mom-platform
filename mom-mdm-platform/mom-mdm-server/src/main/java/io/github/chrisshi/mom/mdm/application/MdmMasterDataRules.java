package io.github.chrisshi.mom.mdm.application;

/**
 * 第一组 MDM 主数据共享的最小输入规则。
 *
 * <p>该类只统一字段长度、空白和 ENABLED/DISABLED 校验，不建立通用状态机、仓储 Capability 或动态规则
 * Framework。输入非法时在写库前失败，不产生副作用；Code 保留大小写，不做隐藏归一化。</p>
 */
public final class MdmMasterDataRules {
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private MdmMasterDataRules() {
    }

    /** 校验并去除技术 ID 首尾空白。 */
    public static String id(String value, String field) {
        return required(value, field, 19);
    }

    /** 校验业务 Code；创建后不再通过普通更新入口接收。 */
    public static String code(String value) {
        return required(value, "code", 64);
    }

    /** 校验面向业务用户的必填名称；多语言显示由未来独立翻译表承载。 */
    public static String name(String value) {
        return required(value, "name", 200);
    }

    /** 校验状态只允许 ENABLED 或 DISABLED。 */
    public static String status(String value) {
        if (ENABLED.equals(value) || DISABLED.equals(value)) {
            return value;
        }
        throw invalid("status 只允许 ENABLED 或 DISABLED");
    }

    /**
     * 要求被引用的父级主数据处于启用状态。
     *
     * @param status 父级当前持久化状态
     * @param resourceName 用于脱敏错误提示的资源名称
     * @throws MdmException 父级不是 ENABLED 时抛出稳定冲突异常
     */
    public static void requireEnabled(String status, String resourceName) {
        if (!ENABLED.equals(status)) {
            throw MdmException.parentDisabled(resourceName);
        }
    }

    /** 校验非负乐观锁版本。 */
    public static long version(Long value) {
        if (value == null || value < 0) {
            throw invalid("version 必须是非负整数");
        }
        return value;
    }

    /**
     * 校验调用方读取到的版本与当前持久化版本一致。
     *
     * @param actual 当前实体版本
     * @param expected 调用方提交的期望版本
     * @throws MdmException 期望版本非法或与当前版本不一致时抛出稳定冲突异常
     */
    public static void requireVersion(Long actual, Long expected) {
        long validated = version(expected);
        if (actual == null || actual != validated) {
            throw MdmException.versionConflict();
        }
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        String result = value.strip();
        if (result.length() > maxLength) {
            throw invalid(field + " 长度不能超过 " + maxLength);
        }
        return result;
    }

    private static MdmException invalid(String message) {
        return new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.validation_failed", "mdm.error.validation_failed");
    }
}
