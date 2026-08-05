# IAM 内置管理员初始化

## 安全边界

- Bootstrap 默认关闭，只允许在非 `prod`、非 `production` Profile 使用。
- 固定用户名为 `admin`，部署环境不能覆盖为其他用户名。
- 临时密码只通过进程环境变量 `IAM_BOOTSTRAP_PASSWORD` 注入，不写入源码、Flyway、配置文件或仓库 `.env`。
- 初始化只执行一次；已有系统账号时不会重置密码、状态、锁定计数、版本或角色。
- 初始化完成后应停止进程，移除 Bootstrap 环境变量并以默认关闭状态重新启动。

## macOS / Linux

先构建 IAM：

```bash
mvn -pl mom-iam-platform/mom-iam-server -am package
```

再以一次性临时密码启动：

```bash
IAM_BOOTSTRAP_ENABLED=true \
IAM_BOOTSTRAP_PASSWORD='<LOCAL_TEMPORARY_PASSWORD>' \
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar
```

## Windows PowerShell

先构建 IAM：

```powershell
mvn -pl mom-iam-platform/mom-iam-server -am package
```

再以一次性临时密码启动：

```powershell
$env:IAM_BOOTSTRAP_ENABLED = "true"
$env:IAM_BOOTSTRAP_PASSWORD = "<LOCAL_TEMPORARY_PASSWORD>"
java -jar mom-iam-platform/mom-iam-server/target/mom-iam-server-0.1.0-SNAPSHOT-exec.jar
```

初始化日志只会显示：

```text
IAM built-in administrator initialized: username=admin
```

日志不会显示密码或密码摘要。确认初始化成功后，停止进程并清除环境变量：

```powershell
Remove-Item Env:IAM_BOOTSTRAP_ENABLED
Remove-Item Env:IAM_BOOTSTRAP_PASSWORD
```

## 首次登录

1. 启动 IAM、Gateway 与 MOM Admin。
2. 使用用户名 `admin` 和一次性临时密码进入 IAM 登录页。
3. IAM 检测到 `password_change_required=true` 后跳转 `/password/change`。
4. 修改密码后继续原 Authorization Code + PKCE 请求并进入 MOM Admin。
5. 后续启动保持 `IAM_BOOTSTRAP_ENABLED=false` 或不设置该变量。

Bootstrap 不提供 `/bootstrap`、`/init-admin` 或其他匿名 HTTP 初始化接口。

已有内置管理员不会被 Bootstrap 更新。唯一管理员遗忘凭据时，必须使用
[IAM 内置管理员一次性恢复](IAM内置管理员恢复.md)，不得通过重复启用 Bootstrap、删除账号或直接写数据库绕过审计。
