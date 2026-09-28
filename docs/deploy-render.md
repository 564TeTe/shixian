# 免费部署给老师演示：Render + Aiven

适用于当前 Vue + Spring Boot + MySQL 项目。Vue 页面和 Java 接口放在同一个 Render 免费服务中，MySQL 放在 Aiven 免费实例中。

本说明和部署文件准备好后，还需要注册账号、初始化云数据库，并把这些部署文件提交到你自己的远程 Git 仓库。仅在本机添加文件不会自动上线。

## 1. 注册两个账号

- [注册 Render](https://dashboard.render.com/register)，可以使用 GitHub 登录。
- [注册 Aiven](https://console.aiven.io/signup)，创建 **MySQL / Free** 服务。

选择名称明确为 **Free / $0** 的计划。Aiven 免费计划与有期限的付费试用是不同选项；创建页面没有 Free 时先检查账号页面，不要直接选择付费实例。

截至 2026-09-28，Render 免费 Web Service 为 512MB 内存，15 分钟无访问后休眠；Aiven 免费 MySQL 为 1GB 内存和 1GB 存储，无固定到期日，但长期不使用可能停机。演示前提前打开网页并完成一次登录。

## 2. 初始化新的演示数据库

等 Aiven MySQL 显示可连接后，从服务页面记录 Host、Port、User 和 Password。在 MySQL Workbench 中新建连接，使用 Aiven 给出的连接设置和 SSL 配置。

只对专门新建的演示服务执行以下操作：

1. 在 Workbench 打开项目根目录的 `T132.sql`，执行整个文件。它会创建并选中 `t132`，并重建业务表；**不要对已有数据的数据库执行**。
2. 执行 `database/003_teaching_seed.sql`，导入与 11 表结构配套的初始数据。它使用存储过程，需完整执行，不能只执行单条光标语句。
3. 检查 `t132.account`、`t132.course`、`t132.teaching_task` 中已有数据。

普通教学功能完成上面两份脚本即可。此次还要展示智能查询，因此继续按第 6 节执行 `database/005_ai_setup_aiven.sql`。云端用这一份替代原版 `005_ai_setup.sql`、`006_ai_teacher_workload.sql`、`007_ai_read_all_business_tables.sql`，不要再套用原脚本中的本机账号。

不需要执行旧版 `001`、`002`、`004` 迁移；`008`～`010` 是另外的数据填充/调整脚本，不属于基础部署必需步骤。

种子数据中的项目初始为空，课表主要在历史学期。演示课表和历史报表时选 2025-2026 对应学期；演示项目新增时先在当前学期创建教学任务，再添加实验项目。

## 3. 把部署文件提交到自己的 Git 仓库

Render 只能读取远程仓库中的内容。确认远程分支包含以下文件，再创建服务：

- `Dockerfile`
- `.dockerignore`
- `render.yaml`
- `deploy/render/application.yml`

Docker 构建只复制必要源码和报表模板。本机 `application.yml`、`database/generated`、IDE 设置及本地数据库文件不在构建上下文中。

本次演示部署使用 `564TeTe/shixian` 仓库的 `codex/render-demo` 分支。在 Render 选择该分支；`main` 分支暂不包含本次新增的部署文件。

## 4. 在 Render 创建免费服务

推荐使用 **New → Blueprint**，连接包含上述文件的 Git 仓库和分支，由根目录 `render.yaml` 创建服务。核对只有一个名为 `teaching-demo` 的 **Free** Web Service。

按提示填入下面三个应用数据库变量，以及第 6 节的 AI 配置。密码和 API 密钥只填在 Render 控制台：

| 变量 | 填写内容 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | 下方 JDBC URL，替换 Host 和 Port |
| `SPRING_DATASOURCE_USERNAME` | Aiven 服务页面的数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | Aiven 服务页面的数据库密码 |

```text
jdbc:mysql://HOST:PORT/t132?sslMode=REQUIRED&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia%2FShanghai&connectTimeout=10000&socketTimeout=60000
```

这里的 URL 是 JDBC 格式，不要直接粘贴 `mysql://用户名:密码@...` 连接串。SSL 必须启用；数据库端口使用 Aiven 提供的值，可能不是 3306。

如果手动选择 **New → Web Service**，配置为：

| 设置 | 值 |
| --- | --- |
| Language / Runtime | Docker |
| Root Directory | 留空，使用仓库根目录 |
| Dockerfile Path | `./Dockerfile` |
| Docker Build Context | `.` |
| Instance Type | Free |
| Health Check Path | `/springboote51e2/index.html` |
| Environment Variables | 上面的三个数据库变量和第 6 节的五个 AI 变量 |

不需要额外填写 Build Command 或 Start Command。Dockerfile 构建 Vue 后将产物打入 JAR，只运行一个 Java 进程。`PORT` 由 Render 注入，默认端口 10000。

配置关闭了服务的代码自动部署。以后更新远程代码后，在 Render 执行 **Manual Deploy → Deploy latest commit**。Blueprint 文件变动仍可能触发自动同步；演示前可在 Blueprint 的 Settings 中将 Auto Sync 设为 No，需要应用配置变更时再点 Manual Sync。

## 5. 打开并检查演示链接

Render 显示 **Live** 后，使用平台分配的实际域名，访问：

```text
https://你的服务名.onrender.com/springboote51e2/index.html#/login
```

需要保留 `/springboote51e2/index.html` 这段路径。只有域名的根地址没有配置首页。

首次登录选择管理员，初始账号为 `admin`，密码为仓库公开的 `Teaching2026!`。首次登录后在“账号与安全”改成自己的密码，再把演示链接和需要的演示账号交给老师。

演示前逐项确认：

1. 提前打开链接，等免费实例唤醒后登录。
2. 检查学期、课程与课表，以及自己准备好的实验项目。
3. 打开统计报表并导出一个小表格。
4. 如果要展示教师权限，由管理员重置一个演示教师账号的密码，再用该教师登录。

健康检查只确认 Java 进程能提供网页，不能代替数据库连接及登录验证。512MB 适合尝试小规模演示，较大的 Excel 导入、导出仍需事先实测。

## 6. 展示智能查询：Aiven 只读账号 + DeepSeek

用户已确认需要展示此功能，并已有 DeepSeek API 密钥。先完成 `T132.sql` 和 `003_teaching_seed.sql`，再配置 AI；已经导入成功时不要重跑建表脚本。

在 PowerShell 使用 Aiven 的 `avnadmin` 连接，密码在提示出现后输入：

```powershell
mysql --host=你的Aiven主机 --port=你的Aiven端口 --user=avnadmin --password --ssl-mode=REQUIRED --default-character-set=utf8mb4
```

连接后执行（以下路径适用于当前工作目录）：

```sql
SOURCE D:/STUDY/iahfbiwabidubIO/database/005_ai_setup_aiven.sql;
```

脚本完成以下配置：

- 创建 `ai_teacher_workload` 授课视图，使用调用者权限。
- 创建专用账号 `teaching_ai_reader`，允许云端远程连接并要求 SSL。
- 首次创建时由 MySQL 生成随机密码，并在 `Generated password` 列返回。**立即私下保存这条密码，稍后填到 Render；不要把含密码的执行结果截图发到聊天里。**
- 为新建的专用账号仅授予 `t132` 的 `SELECT`，不授予新增、修改、删除数据或管理用户的权限。
- 显示授权结果和授课视图行数。脚本不改变课程、课表和项目数据，重复执行不会重置已有密码，也不会清理已有账号的额外权限。

请专门保留这个账号给 AI 使用，不要给它分配额外角色或管理权限。Aiven 控制台创建用户默认可能带管理员权限，本脚本自行创建专用账号。`SHOW GRANTS` 应只有连接相关的 `USAGE` 和 `t132` 的 `SELECT`，不应出现写权限、`GRANT OPTION` 或角色授权。若出现 SQL 错误，先停止后续步骤并保留不含密码的错误信息。

Aiven 管理员对系统库的权限受到限制，旧版脚本中的全库 `REVOKE ALL PRIVILEGES, GRANT OPTION` 会出现 `ERROR 3879`；当前脚本已移除这条语句。如果已运行旧版，先核对 `SHOW GRANTS`，只有上述两项授权时无需重建课程数据；有额外权限时应单独处理，不能靠重跑脚本清理。

如需更换 AI 账号密码，以 `avnadmin` 执行以下命令，并私下保存新返回的随机密码。它将用于 Render 的 `TEACHING_AI_DB_PASSWORD`，不要把结果中的密码发到聊天或提交到仓库：

```sql
ALTER USER 'teaching_ai_reader'@'%' IDENTIFIED BY RANDOM PASSWORD REQUIRE SSL;
```

随后在 Render 设置：

| 变量 | 填写内容 |
| --- | --- |
| `TEACHING_AI_BASE_URL` | `https://api.deepseek.com` |
| `TEACHING_AI_MODEL` | `deepseek-flash` |
| `TEACHING_AI_API_KEY` | 你自己的 DeepSeek API 密钥 |
| `TEACHING_AI_DB_USER` | `teaching_ai_reader` |
| `TEACHING_AI_DB_PASSWORD` | 上一步首次创建账号时生成的随机密码 |

`render.yaml` 已包含这五项；三个非秘密值已预填，创建 Blueprint 时需手动填写 API 密钥和只读账号密码。这里的 AI 只读密码与 `SPRING_DATASOURCE_PASSWORD` 对应的应用账号密码不同。

`TEACHING_AI_DB_URL` 可以不填，后端会复用 `SPRING_DATASOURCE_URL` 的 Aiven 主机、端口、`t132` 库和 SSL 参数，并换用独立只读账号。没有第三方网关时使用上表的 DeepSeek 官方地址。根据 2026-09-28 的官方文档，当前使用 `deepseek-flash`，旧名称 `deepseek-chat`/`deepseek-reasoner` 已停用；后端对 DeepSeek SQL 请求显式关闭思考模式，并移除旧的 `reasoning_effort=low`。

保存环境变量并部署后，用**管理员**账号进入“智能查询”。教师账号不开放智能查询入口。“检查配置”只证明字段已填写，继续依次验证：

1. 提问“一共有多少门课程”，确认返回课程数量。这类问题走本地规则，用于检查只读数据库连接，不能证明 DeepSeek 已连接。
2. 提问“按学院统计教师账号数量”，确认返回按学院分组的结果。这条问题会请求模型生成查询，用于检查 DeepSeek API 密钥、模型请求和只读数据库的完整链路。

模型调用按 DeepSeek 的规则计费；保留自己的密钥在平台环境变量中即可。遇到鉴权错误或余额不足时，核对密钥所属平台和 DeepSeek 账户状态。

## 遇到问题时

| 现象 | 检查方向 |
| --- | --- |
| 首次打开等待较久 | Render 免费实例正在唤醒，等状态恢复后再登录 |
| 根域名返回 404 | 使用带 `/springboote51e2/index.html#/login` 的完整链接 |
| 页面可打开，登录失败 | 检查 Aiven 是否运行、三个数据库环境变量、`t132` 是否完成初始化，以及 Render 日志 |
| 连接超时或 SSL 错误 | 核对 Aiven Host/Port、SSL 要求和服务访问限制；不要改为关闭 SSL |
| 容器因内存退出 | 查看 Render 日志，先用较小的演示数据；需要更多资源时再评估是否更换部署方案 |
| 页面显示没有数据 | 核对学期选择；初始数据不自动生成实验项目 |

## 本次准备的验证记录（2026-09-28）

- Vue 生产构建成功；有依赖数据库过旧和打包体积提示，不影响本次构建。
- 按 Dockerfile 相同的源码、专用配置和前端产物组合，在隔离目录完成 Maven 打包，JAR 大小约 42.1MiB。
- 检查 JAR：页面引用的 16 个构建资源均存在，报表模板已包含，配置来自 `deploy/render/application.yml`，没有本机账号清单和 AI 凭据文件。
- `render.yaml` 已通过 Render 官方 JSON Schema 校验，专用应用 YAML 已成功解析。
- 现有后端测试共 46 项：38 项通过、4 项跳过、4 项因本机 MySQL `root@localhost` 登录被拒绝而出错，不能宣称全套测试通过。应用启动会维护当前学期，因此需要已初始化且可连接的数据库。
- 新增 DeepSeek 请求测试先复现旧请求失败，修正后 16 项 AI 相关测试全部通过；测试使用本地 HTTP 模拟服务，没有调用真实模型或使用真实 API 密钥。
- 在独立临时 MySQL 8.0.41 中导入基础表、种子及云端 AI 脚本，确认 93 门课程和 269 条授课关系；SSL 登录、只读查询、拒绝写入和系统表访问、拒绝非 SSL 登录均通过。重复执行保持密码、只读授权和数据，不清理其他来源的额外权限。
- 使用开启 `partial_revokes` 且被撤销 `sys` 权限的临时管理员复现旧版全库撤权的 `ERROR 3879`；修正后该受限管理员可完成首次设置、重复执行和随机密码更换，新密码可查询而旧密码被拒绝。此测试模拟权限限制，没有连接用户的 Aiven 实例。
- 本机 Docker 引擎未就绪，尚未完成 Docker 镜像构建、512MB 容器运行及 HTTP 联调；隔离 JAR 构建不等于容器验证。
- 用户已在 Aiven MySQL 8.4 手动执行初始化，截图确认 11 张表、93 门课程、269 条授课关系，以及 AI 账号只有 `USAGE` 和 `t132` 的 `SELECT` 授权。云端只读账号的独立登录及实际模型调用仍待验证。Render 上线仍需在控制台完成 Blueprint 创建并填写连接凭据。

## 官方参考

- [Render 免费服务限制](https://render.com/docs/free)
- [Render Docker 部署](https://render.com/docs/docker)
- [Render Blueprint 配置](https://render.com/docs/blueprint-spec)
- [Aiven 免费 MySQL](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier)
- [Aiven Java 连接说明](https://aiven.io/docs/products/mysql/howto/connect-with-java)
- [Aiven MySQL 用户与默认权限](https://aiven.io/docs/products/mysql/howto/manage-service-users)
- [MySQL 随机密码更换](https://dev.mysql.com/doc/refman/8.4/en/alter-user.html)
- [DeepSeek 当前 API 参数](https://api-docs.deepseek.com/)
- [DeepSeek 思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)
