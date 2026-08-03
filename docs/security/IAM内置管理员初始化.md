# IAM 内置管理员初始化与恢复

## 1. 安全边界

- 内置系统管理员用户名固定为 `admin`。
- Bootstrap 和 Recovery 默认关闭，只允许在非 `prod`、非 `production` Profile 使用。
- Bootstrap 与 Recovery 禁止同时启用。
- 密码只能通过进程环境变量注入，不得写入源码、Flyway、`application.yml` 或仓库 `.env`。
- 当前最低密码长度为 **6 位**；Bootstrap、Recovery 与 IAM 管理端初始/重置密码使用同一下限。
- 日志不会输出密码或密码摘要。
- 执行完成后必须停止进程，删除密码环境变量，并以默认关闭状态重新启动。

## 2. Bootstrap：首次创建 admin

Bootstrap 只在数据库中不存在 `admin` 时创建内置系统账号并赋予 `PLATFORM_ADMIN`。

如果 `admin` 已经存在，Bootstrap 会输出：

```text
IAM built-in administrator already exists; bootstrap skipped
```

它不会覆盖现有密码、状态、锁定计数、版本或角色。忘记现有密码时请使用第 3 节 Recovery。

### macOS / Linux

```bash
mvn -pl mom-iam-platform/mom-iam-server -am package

IAM_BOOTSTRAP_ENABLED=true \
IAM_BOOTSTRAP_PASSWORD='<TEMP_PASSWORD_6_PLUS>' \
IAM_JWK_PRIVATE_KEY='file:/absolute/path/mom-iam-private.pem' \
IAM_JWK_PUBLIC_KEY='file:/absolute/path/mom-iam-public.pem' \
IAM_REFRESH_HMAC_PEPPER='<STABLE_RANDOM_PEPPER>' \
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar
```

### Windows PowerShell

```powershell
mvn -pl mom-iam-platform/mom-iam-server -am package

$env:IAM_BOOTSTRAP_ENABLED = "true"
$env:IAM_BOOTSTRAP_PASSWORD = "<TEMP_PASSWORD_6_PLUS>"
$env:IAM_JWK_PRIVATE_KEY = "file:C:/absolute/path/mom-iam-private.pem"
$env:IAM_JWK_PUBLIC_KEY = "file:C:/absolute/path/mom-iam-public.pem"
$env:IAM_REFRESH_HMAC_PEPPER = "<STABLE_RANDOM_PEPPER>"
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar
```

成功日志：

```text
IAM built-in administrator initialized: username=admin
```

Bootstrap 创建的账号默认要求首次改密。

## 3. Recovery：忘记现有 admin 密码

Recovery 不删除、不重建 `admin`，也不创建第二个管理员。它会：

1. 锁定现有 `admin` 与内置 `PLATFORM_ADMIN`；
2. 确认 `admin` 是未删除的 INTERNAL 系统账号，并仍具有效 PLATFORM_ADMIN；
3. 在一个 PostgreSQL 本地事务中替换密码摘要；
4. 将账号恢复为 `ENABLED`；
5. 清零登录失败次数并清除锁定时间；
6. 推进账号聚合 Version；
7. 事务提交后撤销现有 Session 与 Refresh Token。

### macOS / Linux

```bash
mvn -pl mom-iam-platform/mom-iam-server -am package

IAM_BOOTSTRAP_ENABLED=false \
IAM_ADMIN_RECOVERY_ENABLED=true \
IAM_ADMIN_RECOVERY_PASSWORD='<NEW_PASSWORD_6_PLUS>' \
IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE=false \
IAM_JWK_PRIVATE_KEY='file:/absolute/path/mom-iam-private.pem' \
IAM_JWK_PUBLIC_KEY='file:/absolute/path/mom-iam-public.pem' \
IAM_REFRESH_HMAC_PEPPER='<CURRENT_STABLE_PEPPER>' \
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar
```

### Windows PowerShell

```powershell
mvn -pl mom-iam-platform/mom-iam-server -am package

$env:IAM_BOOTSTRAP_ENABLED = "false"
$env:IAM_ADMIN_RECOVERY_ENABLED = "true"
$env:IAM_ADMIN_RECOVERY_PASSWORD = "<NEW_PASSWORD_6_PLUS>"
$env:IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE = "false"
$env:IAM_JWK_PRIVATE_KEY = "file:C:/absolute/path/mom-iam-private.pem"
$env:IAM_JWK_PUBLIC_KEY = "file:C:/absolute/path/mom-iam-public.pem"
$env:IAM_REFRESH_HMAC_PEPPER = "<CURRENT_STABLE_PEPPER>"
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar
```

成功日志示例：

```text
IAM built-in administrator credential recovered: username=admin, sessionsRevoked=0, forcePasswordChange=false
```

日志中的 Session 数量可能大于 0，但不会包含密码、Token 或摘要。

`IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE=true` 时，新密码只作为临时密码，首次登录后仍需修改；本地直接恢复使用可设为 `false`。

## 4. 本地 RSA Key 与 Pepper

没有现成 Key 时，可在本地生成：

```bash
mkdir -p ~/.mom/iam
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out ~/.mom/iam/mom-iam-private.pem
openssl rsa -pubout -in ~/.mom/iam/mom-iam-private.pem \
  -out ~/.mom/iam/mom-iam-public.pem
openssl rand -hex 32
```

最后一条命令输出可作为 `IAM_REFRESH_HMAC_PEPPER`。同一数据库已有 Refresh Token 时，应使用原有稳定 Pepper；随意更换会使旧 Refresh Token 全部失效。

## 5. 执行后清理

恢复成功后停止 IAM，并清除一次性变量。

### macOS / Linux

```bash
unset IAM_ADMIN_RECOVERY_ENABLED
unset IAM_ADMIN_RECOVERY_PASSWORD
unset IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE
```

### Windows PowerShell

```powershell
Remove-Item Env:IAM_ADMIN_RECOVERY_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:IAM_ADMIN_RECOVERY_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:IAM_ADMIN_RECOVERY_FORCE_PASSWORD_CHANGE -ErrorAction SilentlyContinue
```

随后按正常配置重新启动 IAM。正常运行时：

```text
IAM_BOOTSTRAP_ENABLED=false
IAM_ADMIN_RECOVERY_ENABLED=false
```

Bootstrap 和 Recovery 都不提供匿名 HTTP 初始化或密码重置端点。
