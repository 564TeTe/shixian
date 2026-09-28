# 教师演示部署方案

## 目标与范围

用户需要免费部署现有实验教学项目，供老师通过链接演示。已注册账号并创建 Aiven MySQL 8.4 服务，确认要展示智能查询，已有 DeepSeek API 密钥。
本次准备部署文件、云端 SQL 和中文操作说明；实际数据库凭据与 API 密钥由用户在平台填写，不能宣称已完成云端联调。

## 选择

采用 Render 免费 Docker Web Service + Aiven 免费 MySQL。Oracle 免费 VM 需要自行运维和可用名额，Azure 学生方案需要学生认证；本次演示优先减少操作步骤。

Vue 构建产物放入 Spring Boot JAR 的静态资源目录，只有一个 Java 进程和一个公网域名。保留接口前缀 `/springboote51e2` 与现有 hash 路由，入口使用 `/springboote51e2/index.html#/login`。数据库连接通过 Render 环境变量设置。

## 部署边界

- Docker 使用 Node 22 构建 Vue、Maven/JDK 8 构建 Java、JRE 8 运行，保持项目现有 Java 目标版本。
- Render 明确使用 `plan: free`，读取 `PORT`，监听 `0.0.0.0`，限制 JVM 内存、Tomcat 线程及数据库连接池。
- Docker 构建上下文使用允许列表，只包含应用源码、前端资源、报表模板和专用云配置。本地数据库密码、生成账号、数据库导出、IDE 设置和历史前端资源不得进入镜像。
- 云配置独立于本机 `application.yml`，三个数据库连接变量必须在平台设置。不得把凭据写进 Git。
- 数据库仅由用户在新建的演示库手动初始化，容器启动不执行删表、导入或迁移。说明原始 SQL 的适用范围和公开初始密码的更换步骤。
- AI 查询纳入演示范围。新增独立云端 SQL，以远程来源 `%`、随机初始密码和 SSL 要求创建 `teaching_ai_reader`，只授予 `t132` 的 SELECT，并创建授课视图；原本机 SQL 保持原样。
- Render 配置 DeepSeek 的 `deepseek-flash` 与五个 AI 变量；数据库 URL 复用应用的 SSL 连接地址。当前 DeepSeek 默认启用思考，SQL 请求需显式关闭思考并移除旧的 `reasoning_effort=low`；其他模型请求保持既有行为。
- 免费服务会休眠，演示前要打开链接并验证登录、列表、报表。HTTP 静态页面健康检查不代表数据库已连通。

## 验证与完成条件

1. 验证 Vue 生产构建和后端现有测试/打包。
2. 校验 Render YAML，检查构建上下文和 JAR 内容没有本机配置或账号文件。
3. 如本地 Docker 可运行，构建并限制容器为 512MB，检查静态入口和资源；否则用同一构建方式生成隔离 JAR 并明确记录未完成容器验证。
4. 不修改用户已有的 `start-teaching.ps1` 和 `.vscode/settings.json` 改动。
5. 未创建云服务时不得宣称已上线；最终给出账号注册后的具体步骤。
6. 用本地模拟 HTTP 服务验证 DeepSeek 请求，不使用真实 API 密钥；在隔离临时 MySQL 中验证云端脚本、随机密码登录、只读权限及重复执行。
