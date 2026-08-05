# IAM 内置管理员一次性恢复

## 1. 适用范围

该能力只用于非生产环境中唯一内置系统管理员 `admin` 忘记凭据、且不存在其他可执行
`iam:user:password-reset` 的平台管理员时。它不是普通用户“忘记密码”产品功能，不提供匿名 HTTP 接口，
也不允许恢复其他用户名。

如果仍有其他平台管理员，应优先通过 MOM Admin 的受控凭据重置流程处理，不启用本能力。

## 2. 安全契约

- Base 默认关闭，临时凭据和确认串仅通过进程环境变量注入；
- `prod`、`production` Profile 无条件拒绝启动；
- Bootstrap 与 Recovery 不允许同时启用；
- 目标必须是未删除、已启用、`INTERNAL`、`system_account=true` 且当前拥有有效
  `PLATFORM_ADMIN` 的固定 `admin`；
- 临时凭据使用 IAM 当前 `PasswordEncoder` 生成摘要，明文和摘要不进入日志、异常、Trace 或审计；
- 凭据更新使用账号版本 CAS，同时清除失败次数和临时锁定，并强制下一次登录改密；
- 所有活动 Session 均被撤销；Redis 撤销、安全审计或数据库写入失败时启动 Fail Closed；
- 成功后追加 `iam.admin.credential-recovered` 高风险 SYSTEM 安全审计；
- Recovery 不是幂等业务命令。成功后必须停止实例、清除环境变量并以关闭状态重新启动。

## 3. macOS / Linux

先停止当前 IAM 实例。准备一个仅用于本次恢复、长度为 12～200 位的临时凭据，然后执行：

```bash
IAM_BOOTSTRAP_ENABLED=false \
IAM_ADMIN_RECOVERY_ENABLED=true \
IAM_ADMIN_RECOVERY_PASSWORD='<LOCAL_TEMPORARY_PASSWORD>' \
IAM_ADMIN_RECOVERY_CONFIRMATION='RESET_ADMIN_CREDENTIAL' \
IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE=true \
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar
```

只有看到以下不含 Secret 的成功日志，才表示恢复事务已完成：

```text
IAM built-in administrator recovery completed; remove recovery environment variables and restart; username=admin, revokedSessions=<count>
```

随后停止该实例并清除环境变量：

```bash
unset IAM_ADMIN_RECOVERY_ENABLED
unset IAM_ADMIN_RECOVERY_PASSWORD
unset IAM_ADMIN_RECOVERY_CONFIRMATION
unset IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE
```

以 `IAM_ADMIN_RECOVERY_ENABLED=false` 或不设置恢复变量的方式重新启动 IAM。使用临时凭据登录后，系统必须
进入强制改密流程；完成改密后再继续正常管理操作。

## 4. Windows PowerShell

```powershell
$env:IAM_BOOTSTRAP_ENABLED = "false"
$env:IAM_ADMIN_RECOVERY_ENABLED = "true"
$env:IAM_ADMIN_RECOVERY_PASSWORD = "<LOCAL_TEMPORARY_PASSWORD>"
$env:IAM_ADMIN_RECOVERY_CONFIRMATION = "RESET_ADMIN_CREDENTIAL"
$env:IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE = "true"
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar
```

成功后停止实例并清除变量：

```powershell
Remove-Item Env:IAM_ADMIN_RECOVERY_ENABLED
Remove-Item Env:IAM_ADMIN_RECOVERY_PASSWORD
Remove-Item Env:IAM_ADMIN_RECOVERY_CONFIRMATION
Remove-Item Env:IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE
```

## 5. 失败处理

|失败|含义|处理|
|---|---|---|
|生产 Profile 拒绝|恢复能力不允许进入正式环境|使用受控管理员流程，不绕过校验|
|Bootstrap 与 Recovery 同时启用|两个启动写能力存在冲突|保持 Bootstrap 关闭后重新启动|
|管理员不存在|系统尚未初始化|关闭 Recovery，按 Bootstrap 文档初始化|
|账号不是有效系统平台管理员|账号或角色不变量异常|先调查数据和审计，不直接改表|
|版本冲突|另一个实例或操作修改了账号|停止多余实例，重新读取后再执行一次|
|Session/Redis 撤销失败|旧会话无法证明全部失效|恢复事务失败；修复 Redis 后重试|
|安全审计失败|无法留下恢复证据|恢复事务失败；修复审计持久化后重试|

禁止删除数据库、修改历史 Migration、手工写入明文密码、关闭生产校验、绕过 Session 撤销或把失败日志描述为恢复成功。
