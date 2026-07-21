package io.github.chrisshi.mom.iam.domain.type;

/** MOM 应用稳定编码；枚举名称直接持久化，新增值必须通过 Flyway 迁移。 */
public enum ApplicationCode {
    /** MOM 管理端。 */ MOM_ADMIN,
    /** 供应商门户。 */ MOM_SUPPLIER_PORTAL,
    /** 客户门户。 */ MOM_CUSTOMER_PORTAL,
    /** 移动 PDA。 */ MOM_MOBILE_PDA
}
