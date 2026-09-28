# Render 教师演示部署实施计划

> **For agentic workers:** Use the executing-plans workflow to implement the checked tasks in this session. Preserve all pre-existing user changes.

**Goal:** 为当前 Vue + Spring Boot + MySQL 项目准备可验证的免费演示部署配置。

**Architecture:** Vue 静态文件打入 Java JAR，Render 免费 Docker 服务运行单进程；Aiven 托管 MySQL，连接凭据由平台环境变量提供。

**Tech Stack:** Node 22、Vue CLI 4、Maven 3.9、Java 8、Render Blueprint、Aiven MySQL。

---

### Task 1: 部署文件

- [x] 新增 `.dockerignore`，仅允许构建所需的文件进入 Docker 上下文。
- [x] 新增 `Dockerfile`，分别构建 Vue 和 Java，将 Vue 产物打入 JAR，使用非 root JRE 运行。
- [x] 新增 `deploy/render/application.yml`，保留既有接口前缀并配置端口、资源上限与必填数据库环境变量。
- [x] 新增 `render.yaml`，选择 Docker、免费计划、静态页面健康检查和三个由用户填写的数据库变量。

### Task 2: 部署说明

- [x] 新增 `docs/deploy-render.md`，覆盖账号注册、免费 MySQL、仅新库初始化、Render 导入、环境变量、访问地址和演示前验证。
- [x] 明确账号尚未注册、配置尚未提交远程；不把准备完成表述为已经上线。
- [x] 区分普通功能与可选 AI 配置，不执行数据库初始化或数据上传，不上传本机真实数据和凭据。

### Task 3: 验证与审查

- [x] `node node_modules/@vue/cli-service/bin/vue-cli-service.js build`，设置 `NODE_OPTIONS=--openssl-legacy-provider`，输出到 `.git/codex-deploy-check/frontend`，构建通过。
- [x] 运行完整 Maven 测试并记录实际结果：46 项中 38 项通过、4 项跳过、4 项因本机数据库登录失败而出错。未修改本机数据库凭据。
- [x] Docker 引擎未就绪，完成隔离 Maven 打包与 JAR 内容检查，并记录未完成容器验证的限制。
- [ ] 账号及数据库就绪后请求 `/springboote51e2/index.html`、构建资源并完成登录/报表联调。当前仅核对打包资源及映射，没有 HTTP 运行结论。
- [x] 校验 Blueprint 与专用 YAML，检查最终 diff，记录全部验证结果及未验证事项。

配置改动通过实际构建和运行验证，不新增仅复述配置值的测试。

### Task 4: Aiven + DeepSeek 智能查询演示

- [x] 新增 `database/005_ai_setup_aiven.sql`，创建随机密码、SSL 必需的专用远程只读账号，以及调用者权限的授课视图。
- [x] 为 DeepSeek 请求兼容性先添加 HTTP 模拟测试，确认旧请求失败，再按当前官方接口关闭思考并移除旧努力参数；保留其他模型请求行为。
- [x] 补齐 `render.yaml` 的五个 AI 变量，以及文档中的完整云端初始化顺序、随机密码保存、DeepSeek 参数和实际查询验收步骤；区分本地规则查询与真实模型查询。
- [x] 在隔离 MySQL 8.0.41 中验证基础种子、云端 AI 脚本、重复执行、SSL 只读登录、写操作和系统表访问拒绝；没有连接或修改用户本地业务库和 Aiven 实例。
- [x] 16 项 AI 相关后端测试通过，Render Schema 校验通过，完成只读代码审查并修正验收示例。
- [x] 用户已在 Aiven MySQL 8.4 手动执行脚本，截图确认 11 张表、93 门课程、269 条授课关系及仅 `USAGE`/`SELECT` 的 AI 账号授权。
- [x] 复现 Aiven 受限管理员执行全库撤权的 `ERROR 3879`，移除不兼容语句，明确重跑不清理额外权限；隔离验证受限管理员设置、重复执行及密码更换。
- [ ] 验证云端只读登录，部署 Render 后使用用户自行填写的数据库凭据和 API 密钥完成 DeepSeek 真实调用。
