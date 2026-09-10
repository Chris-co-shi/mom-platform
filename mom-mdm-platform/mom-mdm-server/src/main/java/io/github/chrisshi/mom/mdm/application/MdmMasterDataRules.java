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

    /** 校验中文名称。 */
    public static String nameZh(String value) {
        return required(value, "nameZh", 200);
    }

    /** 校验可空英文名称；空白值统一保存为 null。 */
    public static String nameEn(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.strip();
        if (result.length() > 200) {
            throw invalid("nameEn 长度不能超过 200");
        }
        return result;
    }

    /** 校验状态只允许 ENABLED 或 DISABLED。 */
    public static String status(String value) {
        if (ENABLED.equals(value) || DISABLED.equals(value)) {
            return value;
        }
        throw invalid("status 只允许 ENABLED 或 DISABLED");
    }

    /** 校验非负乐观锁版本。 */
    public static long version(Long value) {
        if (value == null || value < 0) {
            throw invalid("version 必须是非负整数");
        }
        return value;
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
        return new MdmException(MdmException.Kind.BAD_REQUEST, "mdm.validation_failed", message);
    }
}
