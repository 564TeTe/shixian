# 实验教学项目管理系统

> 2026-09-11 更新：当前后端已适配新版 `T132.sql` 的 11 表结构（统一 `account` 账号、合并学年学期）。旧后端模块已按实际前端调用清理，见 [清理、适配与验证说明](docs/java-cleanup.md)。下文 13 表、001/002/004 迁移及历史数据数量属于旧版本记录，不适用于新建的 11 表数据库。新库按 `T132.sql`、`database/003_teaching_seed.sql` 初始化，已有数据的库不要重跑建表脚本。

基于 Spring Boot 2.2、Vue 2、Element UI、MySQL 8 改造的教学工作台。原有演示数据已备份后清理，旧预约、学生、采购等表已删除，相关业务入口已关闭。

## 已实现

- 管理员／教师登录、密码修改、教师教学任务范围隔离。
- 教学概览、学期管理、课程与逐周课表、当前学期任务创建。
- 实验项目增删改、Excel 导入和同课程复制；历史学期只读。
- 实验室与教师账号维护、密码重置、Excel 导入。
- 课表导入、批次原始行核对、重复文件幂等、疑似重复任务逐行确认。
- 实验室学时／人时报表、实验项目报表及 Excel 导出。
- 管理员自然语言查询：模型生成 SQL、白名单校验、独立只读数据库执行。无模型配置时如实提示。

## 本机启动

后端统一使用 `9090`，接口前缀为 `http://localhost:9090/springboote51e2`；Vue 前端使用 `8081`，开发代理转发到后端 `9090`。

在 IDEA 中运行 `back/src/main/java/com/SpringbootSchemaApplication.java` 的 `main` 方法启动后端。若运行配置中设置了 `server.port`、`--server.port` 或 `-Dserver.port`，请删除该覆盖项或设为 `9090`。随后在 IDEA 终端执行：

```powershell
cd D:\shixian\front
npm run serve
```

浏览器打开 `http://localhost:8081`。修改 `vue.config.js` 后需停止并重新运行 `npm run serve`；修改后端端口后需重启后端。也可使用下方脚本启动。

访问 http://localhost:8081 ，选择管理员，用户名 `admin`。随机初始密码及教师临时账号在本机 `database/generated/teaching-accounts.local.json`，不提交 Git。修改密码后该初始清单不会更新。

```powershell
.\start-teaching.ps1
# 更新代码后构建并重启脚本管理的后端：
.\start-teaching.ps1 -Build -Restart
```

需要 Java、Maven、Node.js、MySQL 8。首次启动前在 `front` 执行 `npm ci`。数据库连接在 `back/src/main/resources/application.yml`，可由 Spring 的 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 环境变量覆盖。日志在 `database/generated/`。

实际数据：2025-2026 历史课表，93 门课程、255 个任务、2,208 条排课、79 个临时教师账号、14 间实验室。当前日期对应 2026-2027 第一学期，请先在“课程与课表”创建当前任务或导入当前课表，再录入项目。

项目文件是模板，示例不作为正式数据，因此项目初始为 0。真实工号、负责人和设备数需要补齐；课表疑点保留在导入中心。

## 新环境初始化

仓库已附带老师课表对应的教学数据，**不需要原始 Excel**。在项目根目录打开 MySQL 客户端（`-p` 输入别人自己电脑的 MySQL 密码）：

```powershell
mysql --host=127.0.0.1 --port=3306 --user=root -p --default-character-set=utf8mb4
```

在 MySQL 提示符中依次执行：

```sql
CREATE DATABASE t132 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE t132;
SOURCE T132.sql;
SOURCE database/003_teaching_seed.sql;
SOURCE database/006_ai_teacher_workload.sql;
SOURCE database/007_ai_read_all_business_tables.sql;
```

使用 Workbench 时，先创建并选中 `t132`，再分别打开 `T132.sql`、`database/003_teaching_seed.sql`、`database/006_ai_teacher_workload.sql`、`database/007_ai_read_all_business_tables.sql`，按顺序执行整个文件。`SOURCE` 是命令行客户端的命令。

- `T132.sql`：13 张表的结构，表名与字段名均为英文，全部带中文注释；`003_teaching_seed.sql`：93 门课程、255 个任务、2,208 条排课、79 个教师、14 间课表实验室、原始导入记录。项目模板示例未导入，正式项目为 0。
- **新环境网页管理员：`admin` / `Teaching2026!`**。这是公开的开发初始化密码，登录后在“账号与安全”修改。教师使用新的随机密码摘要，由管理员在“教师账号”重置后分发。
- SQL 不包含本机管理员／教师密码、登录令牌、数据库账号权限或 AI 密钥。本机原有登录凭据不变。
- 数据脚本只用于空表初始化，重复导入会报错，不会覆盖已有内容。不要在已使用的数据库上重跑，也不要为重跑而删除自己的数据库。

接着把后端数据库连接改为自己的 MySQL 配置，在 `front` 执行 `npm ci`，回到根目录执行 `.\start-teaching.ps1 -Build`，访问 http://localhost:8081 。首次无需设置 AI 即可使用普通教学功能。

已经执行过旧版 `T132.sql` 和 `003_teaching_seed.sql` 的数据库，先停后端并备份，再在 MySQL 中执行以下增量迁移，保留现有账号、密码与教学数据：

```sql
USE t132;
SOURCE database/004_english_schema.sql;
```

迁移后执行 `.\start-teaching.ps1 -Build -Restart`。不要在已有数据的库上重新执行 `T132.sql`，它含有删表语句。新环境只执行新版 `T132.sql` 和 `003_teaching_seed.sql` 即可。

更早期尚未建立教学表的旧库按 001、002、004 顺序升级。001、002 为历史迁移，不应在英文结构上重跑。若 AI 只读账号原先仅获授 `shiyanshixinxi` 的表级权限，需改授 `laboratory` 的 SELECT 权限。字段对照及注释查看方式见 [英文命名迁移说明](docs/database/03-英文命名与注释.md)。需要从其他 Excel 重新清理初始化时才使用 `bootstrap_teaching.py --reset-legacy-data`。详见 [建库与导入说明](docs/database/02-建库与导入操作说明.md)。

## AI 配置

在启动后端的进程环境中设置以下变量，再重启：

- `TEACHING_AI_BASE_URL`：兼容 Chat Completions 的服务地址，例如供应商的 `/v1` 地址。
- `TEACHING_AI_API_KEY`、`TEACHING_AI_MODEL`：供应商密钥和模型名。
- `TEACHING_AI_DB_USER`、`TEACHING_AI_DB_PASSWORD`：独立只读账号；禁止 root。
- 可选 `TEACHING_AI_DB_URL`：只读连接 JDBC URL。

本机只读凭据已保存在忽略提交的 `database/generated/ai-database.local.json`，启动脚本会读取。新环境应仅授予业务表 SELECT 权限。模型收到用户问题与业务字段结构，不发送密码、令牌或密钥。
教师授课统计通过 `ai_teacher_workload` 只读视图完成，只暴露教师姓名、教学任务、课程和班级组成。
`007_ai_read_all_business_tables.sql` 允许只读账号读取 `t132` 的全部表和视图；后端会拒绝密码、令牌和密钥字段，并只执行经过 AST 安全校验的单条 SELECT。

真实远程模型尚未配置。本地模拟模型验证只覆盖调用、SQL 校验及只读执行链路，不能代替供应商接入验证。

## 验证与来源

后端在 `back` 执行 `mvn test`；前端在 `front` 执行 `npm run build`，新 Node 可设置 `NODE_OPTIONS=--openssl-legacy-provider`。Python 解析器执行 `python -m unittest discover -s tools/database/tests -v`。

真实 MySQL 回滚测试需设置 `TEACHING_DB_TEST=1` 及 `TEACHING_TEST_DB_URL`、`TEACHING_TEST_DB_USER`、`TEACHING_TEST_DB_PASSWORD`。HTTP 联调脚本为 `tools/database/smoke_teaching.py`，只清理自身创建的测试数据。报告见 `docs/database/reports/`，旧建库阶段报告为历史记录。

原框架来源：[LaboratoryManagementSystem](https://github.com/zongjixiaoai66/LaboratoryManagementSystem)。
