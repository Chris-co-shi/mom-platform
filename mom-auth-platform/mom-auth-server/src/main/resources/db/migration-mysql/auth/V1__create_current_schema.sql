-- auth：根据 PostgreSQL 17 空库 Flyway 迁移至 V25 后的有效结构建立 MySQL 8.4 基线。

CREATE TABLE auth_i18n_message_definition (
    id varchar(19) NOT NULL COMMENT '应用侧 ASSIGN_ID 生成的 varchar(19) 技术主键',
    message_key varchar(160) NOT NULL COMMENT 'Owner 内唯一且创建后不可修改的小写稳定消息键',
    description varchar(1000),
    enabled boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标记',
    created_by varchar(128) NOT NULL,
    created_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    CONSTRAINT ck_auth_i18n_message_definition_key CHECK ((regexp_like(message_key, '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*\\z', 'c'))),
    CONSTRAINT ck_auth_i18n_message_definition_version CHECK ((version >= 0)),
    CONSTRAINT pk_auth_i18n_message_definition PRIMARY KEY (id),
    CONSTRAINT uk_auth_i18n_message_definition_key UNIQUE (message_key),
    KEY ix_auth_i18n_message_definition_runtime (deleted, enabled, message_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Auth Owner 内唯一、不可变的稳定 I18n Key 定义';

CREATE TABLE auth_i18n_translation (
    id varchar(19) NOT NULL COMMENT '应用侧 ASSIGN_ID 生成的 varchar(19) 技术主键',
    message_id varchar(19) NOT NULL COMMENT '同 Schema auth_i18n_message_definition 技术主键引用，无跨 Schema 外键',
    locale_code varchar(35) NOT NULL,
    message_text varchar(4096) NOT NULL,
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标记',
    created_by varchar(128) NOT NULL,
    created_at datetime(6) NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at datetime(6) NOT NULL,
    CONSTRAINT ck_auth_i18n_translation_locale CHECK ((regexp_like(locale_code, '^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?\\z', 'c'))),
    CONSTRAINT ck_auth_i18n_translation_text CHECK ((((char_length((message_text)) >= 1) AND (char_length((message_text)) <= 4096)) AND (NOT regexp_like(message_text, '[\\x01-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]', 'c')) AND (NOT regexp_like(message_text, '<[^>]*>', 'c')))),
    CONSTRAINT ck_auth_i18n_translation_version CHECK ((version >= 0)),
    CONSTRAINT pk_auth_i18n_translation PRIMARY KEY (id),
    CONSTRAINT uk_auth_i18n_translation_message_locale UNIQUE (message_id, locale_code),
    KEY ix_auth_i18n_translation_runtime (deleted, locale_code, message_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Auth 自有动态国际化译文；locale_code 为平台 Locale 的逻辑引用';

CREATE TABLE auth_permission (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)',
    code varchar(160) NOT NULL COMMENT '全局唯一权限编码，推荐使用 domain:resource:action，例如 mes:work-order:create',
    name varchar(200) NOT NULL COMMENT '权限展示名称',
    description varchar(1000) COMMENT '权限用途说明，可为空',
    enabled boolean NOT NULL DEFAULT true COMMENT '权限是否参与授权计算；true 有效，false 停用',
    created_at datetime(6) NOT NULL COMMENT '权限首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code',
    updated_at datetime(6) NOT NULL COMMENT '权限最近修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号，新记录从 0 开始',
    deleted boolean NOT NULL DEFAULT false COMMENT 'MyBatis-Plus 逻辑删除标识，false 有效，true 已删除',
    resource_id varchar(19) NOT NULL COMMENT '所属 Auth Permission Resource 技术主键，由 Application 校验存在性',
    action_code varchar(60) NOT NULL COMMENT '资源下的稳定动作编码，统一大写；完整 authority code 由服务端生成',
    CONSTRAINT ck_auth_permission_action_format CHECK ((regexp_like(action_code, '^[A-Z][A-Z0-9_-]*\\z', 'c'))),
    CONSTRAINT ck_auth_permission_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_auth_permission PRIMARY KEY (id),
    CONSTRAINT uk_auth_permission_code UNIQUE (code),
    CONSTRAINT uk_auth_permission_resource_action UNIQUE (resource_id, action_code),
    KEY ix_auth_permission_enabled (deleted, enabled),
    KEY ix_auth_permission_resource_code (deleted, resource_id, code, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Mini Auth V1 权限目录；Permission Code 最终转换为 Spring Security GrantedAuthority';

CREATE TABLE auth_permission_resource (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键',
    domain_code varchar(32) NOT NULL COMMENT '授权域稳定编码，统一大写',
    resource_code varchar(64) NOT NULL COMMENT '域内业务资源稳定编码，统一大写',
    name varchar(200) NOT NULL COMMENT '资源展示名称，不参与权限判断',
    description varchar(1000) COMMENT '资源用途说明',
    sort_order int NOT NULL DEFAULT 0 COMMENT '目录显示顺序，小值在前',
    enabled boolean NOT NULL DEFAULT true COMMENT '是否允许新建或重新启用该资源下的权限，不级联修改现有权限',
    created_at datetime(6) NOT NULL COMMENT '创建 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '可信创建 Actor',
    updated_at datetime(6) NOT NULL COMMENT '最近修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor',
    version bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted boolean NOT NULL DEFAULT false COMMENT '逻辑删除标识，false 表示有效；资源被权限引用时禁止删除',
    CONSTRAINT ck_auth_permission_resource_code_format CHECK ((regexp_like(resource_code, '^[A-Z][A-Z0-9_-]*\\z', 'c'))),
    CONSTRAINT ck_auth_permission_resource_domain_format CHECK ((regexp_like(domain_code, '^[A-Z][A-Z0-9_-]*\\z', 'c'))),
    CONSTRAINT ck_auth_permission_resource_sort_non_negative CHECK ((sort_order >= 0)),
    CONSTRAINT ck_auth_permission_resource_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_auth_permission_resource PRIMARY KEY (id),
    CONSTRAINT uk_auth_permission_resource_domain_code UNIQUE (domain_code, resource_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Auth 权限目录中的业务授权资源，不是菜单、路由或运行时 authority';

CREATE TABLE auth_role (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)',
    code varchar(100) NOT NULL COMMENT '全局唯一角色编码，用于稳定识别角色；逻辑删除后默认不复用原编码',
    name varchar(200) NOT NULL COMMENT '角色展示名称',
    description varchar(1000) COMMENT '角色职责说明，可为空',
    enabled boolean NOT NULL DEFAULT true COMMENT '角色是否参与授权计算；true 有效，false 停用',
    created_at datetime(6) NOT NULL COMMENT '角色首次持久化 UTC 时间',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code',
    updated_at datetime(6) NOT NULL COMMENT '角色最近修改 UTC 时间',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号，新记录从 0 开始',
    deleted boolean NOT NULL DEFAULT false COMMENT 'MyBatis-Plus 逻辑删除标识，false 有效，true 已删除',
    CONSTRAINT ck_auth_role_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_auth_role PRIMARY KEY (id),
    CONSTRAINT uk_auth_role_code UNIQUE (code),
    KEY ix_auth_role_enabled (deleted, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Mini Auth V1 角色；角色表示职责集合，通过关系表关联用户和 Permission';

CREATE TABLE auth_role_permission (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)',
    role_id varchar(19) NOT NULL COMMENT 'auth_role.id 引用；存在性和删除保护由 Application Service 保证',
    permission_id varchar(19) NOT NULL COMMENT 'auth_permission.id 引用；存在性和删除保护由 Application Service 保证',
    created_at datetime(6) NOT NULL COMMENT '关系首次创建 UTC 时间；关系移除采用物理删除，重新授权时创建新记录',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code',
    CONSTRAINT pk_auth_role_permission PRIMARY KEY (id),
    CONSTRAINT uk_auth_role_permission_role_permission UNIQUE (role_id, permission_id),
    KEY ix_auth_role_permission_permission (permission_id),
    KEY ix_auth_role_permission_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='角色与权限关系；同一角色与 Permission 组合只允许一条记录；不建立物理外键';

CREATE TABLE auth_user (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)',
    username varchar(120) NOT NULL COMMENT '全局唯一登录账号；应用层负责 trim 和 lowercase 规范化，逻辑删除后默认不复用原账号',
    password_hash varchar(255) NOT NULL COMMENT '密码摘要，仅认证流程可读取；禁止返回 API、写入日志、Trace 或审计事件',
    display_name varchar(200) NOT NULL COMMENT '账号展示名称，仅用于界面显示，不作为登录标识或授权依据',
    enabled boolean NOT NULL DEFAULT true COMMENT '账号是否允许认证；true 可登录，false 禁止登录',
    created_at datetime(6) NOT NULL COMMENT '记录首次持久化 UTC 时间，由服务端审计处理器或 Flyway 显式写入',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code',
    updated_at datetime(6) NOT NULL COMMENT '最近一次持久化修改 UTC 时间，由服务端审计处理器维护',
    updated_by varchar(128) NOT NULL COMMENT '最近修改 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code',
    version bigint NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus 乐观锁版本号，新记录从 0 开始',
    deleted boolean NOT NULL DEFAULT false COMMENT 'MyBatis-Plus 逻辑删除标识，false 有效，true 已删除',
    CONSTRAINT ck_auth_user_version_non_negative CHECK ((version >= 0)),
    CONSTRAINT pk_auth_user PRIMARY KEY (id),
    CONSTRAINT uk_auth_user_username UNIQUE (username),
    KEY ix_auth_user_enabled (deleted, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='Mini Auth V1 登录账号；保存账号标识、密码摘要、展示名称和启停状态，不承载角色、组织、员工、Session 或 Token 数据';

CREATE TABLE auth_user_role (
    id varchar(19) NOT NULL COMMENT 'MOM String 技术主键，Java 使用 String，数据库固定 varchar(19)',
    user_id varchar(19) NOT NULL COMMENT 'auth_user.id 引用；存在性和删除保护由 Application Service 保证',
    role_id varchar(19) NOT NULL COMMENT 'auth_role.id 引用；存在性和删除保护由 Application Service 保证',
    created_at datetime(6) NOT NULL COMMENT '关系首次创建 UTC 时间；关系移除采用物理删除，重新分配时创建新记录',
    created_by varchar(128) NOT NULL COMMENT '创建 Actor ID，可保存用户 ID 或稳定 SYSTEM Actor Code',
    CONSTRAINT pk_auth_user_role PRIMARY KEY (id),
    CONSTRAINT uk_auth_user_role_user_role UNIQUE (user_id, role_id),
    KEY ix_auth_user_role_role (role_id),
    KEY ix_auth_user_role_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='用户与角色关系；一个用户可拥有多个角色，同一用户与角色组合只允许一条记录；不建立物理外键';
