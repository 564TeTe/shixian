# 实验教学项目管理系统

基于 Spring Boot 2.2、Vue 2、Element UI、MySQL 8 改造的教学工作台。原有演示数据已备份后清理，旧预约、学生、采购等业务入口已关闭。

## 已实现

- 管理员／教师登录、密码修改、教师教学任务范围隔离。
- 教学概览、学期管理、课程与逐周课表、当前学期任务创建。
- 实验项目增删改、Excel 导入和同课程复制；历史学期只读。
- 实验室与教师账号维护、密码重置、Excel 导入。
- 课表导入、批次原始行核对、重复文件幂等、疑似重复任务逐行确认。
- 实验室学时／人时报表、实验项目报表及 Excel 导出。
- 管理员自然语言查询：模型生成 SQL、白名单校验、独立只读数据库执行。无模型配置时如实提示。

## 本机启动

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

`T132.sql` 现为纯表结构，无演示记录或账号密码，导入空的 MySQL 8 数据库。已有旧库升级使用 `database/001_teaching_foundation.sql` 与 `database/002_teaching_application.sql`。

初始化工具先完整备份，再清理现有业务数据并从指定真实课表初始化，必须显式传入 `--reset-legacy-data`。本机已完成，日常启动不要重跑。详见 [建库与导入说明](docs/database/02-建库与导入操作说明.md)。

## AI 配置

在启动后端的进程环境中设置以下变量，再重启：

- `TEACHING_AI_BASE_URL`：兼容 Chat Completions 的服务地址，例如供应商的 `/v1` 地址。
- `TEACHING_AI_API_KEY`、`TEACHING_AI_MODEL`：供应商密钥和模型名。
- `TEACHING_AI_DB_USER`、`TEACHING_AI_DB_PASSWORD`：独立只读账号；禁止 root。
- 可选 `TEACHING_AI_DB_URL`：只读连接 JDBC URL。

本机只读凭据已保存在忽略提交的 `database/generated/ai-database.local.json`，启动脚本会读取。新环境应仅授予业务表 SELECT 权限。模型收到用户问题与业务字段结构，不发送账号表和密码。

真实远程模型尚未配置。本地模拟模型验证只覆盖调用、SQL 校验及只读执行链路，不能代替供应商接入验证。

## 验证与来源

后端在 `back` 执行 `mvn test`；前端在 `front` 执行 `npm run build`，新 Node 可设置 `NODE_OPTIONS=--openssl-legacy-provider`。Python 解析器执行 `python -m unittest discover -s tools/database/tests -v`。

真实 MySQL 回滚测试需设置 `TEACHING_DB_TEST=1` 及 `TEACHING_TEST_DB_URL`、`TEACHING_TEST_DB_USER`、`TEACHING_TEST_DB_PASSWORD`。HTTP 联调脚本为 `tools/database/smoke_teaching.py`，只清理自身创建的测试数据。报告见 `docs/database/reports/`，旧建库阶段报告为历史记录。

原框架来源：[LaboratoryManagementSystem](https://github.com/zongjixiaoai66/LaboratoryManagementSystem)。
