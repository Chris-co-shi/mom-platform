# MOM 本地 OpenAPI 契约

Auth、System、MDM 在 `local` Profile 下生成 OpenAPI 3.1 文档。Base 与正式部署默认关闭文档端点；
`local` 同时将服务绑定到 `127.0.0.1`，仅在本机匿名开放文档，不放开普通业务 API。
这份文档不替代 Controller、Resource Server、Gateway 配置和真实 HTTP 验证。

| 服务 | 本机 Swagger UI | 原始 OpenAPI JSON | Controller 路径来源 |
|---|---|---|---|
| Auth | `http://127.0.0.1:20001/swagger-ui/index.html` | `http://127.0.0.1:20001/v3/api-docs` | Auth Controller，如 `/login` |
| System | `http://127.0.0.1:20300/swagger-ui/index.html` | `http://127.0.0.1:20300/v3/api-docs` | System Controller，如 `/i18n/locales` |
| MDM | `http://127.0.0.1:20200/swagger-ui/index.html` | `http://127.0.0.1:20200/v3/api-docs` | MDM Controller，目前仍含 `/api/mdm/...` |

启动服务时显式激活 `local`，不要在共享环境或正式环境激活。OpenAPI 只记录服务内部 HTTP 路径；
浏览器 `/api` 前缀、Vite 代理和 Gateway `StripPrefix` 不会自动写入生成结果。检查请求时应逐跳核对：

```text
浏览器 URL → 前端代理后的 Gateway URL → Gateway 转发后的 Controller URL
```

例如当前 Auth 登录链条是 `/api/auth/login → /auth/login → /login`。System 语言目录的目标链条是
`/api/system/i18n/locales → /system/i18n/locales → /i18n/locales`；对应 Gateway 路由和前端代理
必须同时匹配。MDM 路径尚处于对齐阶段，不得从服务内部 `/api/mdm/...` 推断现行 Gateway 对外地址。
当前 System 字典类型启停在服务文档中显示为 `PATCH /{id}/status`，应作为独立路由问题审查，
不在文档接入 Slice 中静默修改。

`local` 文档页可匿名查看契约，但普通管理 API 仍由 Resource Server 验证 Bearer Token 和权限。
具体哪些业务操作需要 `@PreAuthorize`，以 Controller 和实际 401/403 探针为准；不要把 Swagger UI
的“可试调”误认为匿名可调用。OAuth2/OIDC 协议端点若将来启用，应使用协议自身的 Discovery 文档。

版本化快照位于 `snapshots/`，用于 Review 当前 Controller 方法、路径、请求及响应 Schema 的变化。
服务启动后执行：

```bash
bash scripts/export-api-docs.sh --check
bash scripts/export-api-docs.sh --update
```

`--check` 比较当前服务与已提交快照；有合法 API 变更时用 `--update`，Review 差异并明确兼容性。
脚本仅移除每次启动都会变化的 `servers` 地址，不改写路径、状态码或 Schema。
如使用其他本地端口，可分别设置 `MOM_AUTH_DOC_URL`、`MOM_SYSTEM_DOC_URL`、`MOM_MDM_DOC_URL`。
快照不代表全部接口语义已经人工审阅，尤其不能替代权限、错误码与 Gateway 路径的契约测试。
