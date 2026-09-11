# 后端清理与新数据库适配

本次以 `front/src/router/router-static.js` 的实际可达页面、接口和后端间接依赖为依据。`static-front` 是独立原型，不作为删除实际功能的依据。提交记录 cba2643、4528335、9ab78ab 确认孙特负责教学工作流和数据库改造，相关保留实现按开发手册整理。

## 前端功能与保留实现

| 前端功能 | 接口 | 保留实现 |
| --- | --- | --- |
| 两种身份登录、退出 | /users/login、/jiaoshi/login、对应 /logout | AccountController、AccountService、AccountIdentity、AuthorizationInterceptor |
| 首页、下拉数据 | /teaching/dashboard、/lookups | TeachingController、TeachingService |
| 学期、任务、排课详情 | /teaching/terms、/terms/generate、/tasks、/tasks/{id} | TeachingTermService、TeachingService、TeachingAccess |
| 项目维护、跨学期复制 | /teaching/projects、/projects/{id}、/projects/copy | TeachingService、TeachingAccess |
| 教师、实验室与重置密码 | /teaching/teachers、/labs、/teachers/{id}/reset-password | TeachingService、TeachingPasswords |
| 修改本人密码 | /teaching/account/password | TeachingService、TeachingPasswords |
| Excel导入、模板、批次和确认 | /teaching/imports/**、/teaching/templates/{type} | TeachingImportController/Service、TeachingExcel |
| 报表和导出 | /teaching/reports、/reports/export | TeachingReportController/Service |
| 智能查询 | /teaching/ai/status、/ai/query | TeachingAiController/Service、TeachingAiSqlGuard |

还保留启动入口、MVC配置、ApiResponse。上传使用教学控制器 MultipartFile，导出使用 POI，鉴权使用账号和令牌，密码使用 BCrypt，智能查询使用 SQL AST 白名单；这些间接依赖均保留。

## 删除依据

删除学生、旧实验课程及评论、实验室预约、独立设备、采购、维修、公告及评论、知识文章、收藏、轮播配置、旧通用数据库和文件接口。实际前端没有这些业务入口，原拦截器也已禁止调用。

删除其专用 Service、DAO、Entity、Model、VO、View、Mapper，以及仅服务旧模板的分页、查询、反射映射、百度工具和注解。旧 Teacher/User/Token 的 MyBatis 链路由统一账号实现替代；教师管理、实验室管理继续由教学服务提供。

共清理160个旧业务及配套文件，见 [removed-backend-files.txt](removed-backend-files.txt)。清单按整理时工作区英文路径记录；Git 对之前拼音文件显示删除属于同一次清理。另将 R.java 改为 ApiResponse.java。清理阶段保留20个后端 Java 源文件；后续按职责分包并提取共享对象、异常及下载工具后为24个，见 [当前包结构](java-package-structure.md)。

移除11项无引用直接依赖：MyBatis starter、Shiro、MyBatis-Plus及其starter、protobuf、commons-lang3、validation-api、commons-io、Hutool、Fastjson、百度SDK。POI需要的传递依赖仍由Maven解析。

保留用户修改的SQL、数据库连接、已暂存的迁移删除和前端锁文件，仅从 application.yml 移除不再生效的 MyBatis 配置段。历史网页资源、上传资源、数据库工具和 static-front 未删除。

## 新库适配

- 使用当前 T132.sql 的11张表，不执行重建或导入脚本。管理员与教师统一使用 account，按 ADMIN/TEACHER 校验角色。
- 令牌使用 account_id、expires_at。登录锁定账号并替换旧令牌；退出、重置密码撤销令牌。教师重置接口不能操作管理员。
- 学期使用 academic_term.start_year；前端 academic_year_id 保留为分组键，其值为起始年份，报表按同一个值筛选。
- 授课教师、实验室负责人、项目操作人使用账号外键。管理员操作项目也记录真实账号ID。
- 正式任务只保存新表规定的字段。课表完整内容保留在 raw_data，确认时重新解析。排课 hours 是生成列，不再手动写入。
- 新库取消任务默认实验室，创建页移除该字段，地点由实际排课关联。未排课任务保持0排课学时，不虚构排课。
- 学校代码输入上限为5，教师与实验室输入按新表长度校验。Excel项目参与人数保存到 participant_count，报表人时仍使用任务选课人数。
- AI白名单和提示词移除 academic_year，账号和令牌不在可查询白名单内。

登录URL与JSON别名兼容旧前端；Java标识符使用英文，详见 [java-naming.md](java-naming.md)。本次没有把静态原型中的权限调整强行应用到实际业务。

## 手册规范

依据《阿里巴巴Java开发手册》v1.3.0：命名风格第2–4条（英文及大小写）、代码格式第1/5条（大括号、四空格）、控制语句第2条（分支循环大括号）、异常处理第2–4条（不用异常做条件控制、细分异常、保留原因）、其它第8条（清理无用代码）。

统一保留Java代码格式，展开单行控制逻辑，使用显式类型导入；新账号模块使用构造器注入和独立身份对象。整理数值、日期、JSON和SQL解析异常，保留原因并返回统一业务错误。保留授权、事务和参数化SQL。未升级Java/Spring Boot或增加运行依赖。

## 验证

在 back 执行：

```powershell
$env:TEACHING_DB_TEST = '1'
mvn clean package
```

数据库测试优先使用 TEACHING_TEST_DB_URL/USER/PASSWORD，未设置则使用本地 application.yml。测试自行建立临时账号、课程和实验室并回滚，不依赖正式种子数据。Spring启动仍执行项目原有的当前学期自动生成。

覆盖登录角色、令牌失效、重置权限、旧接口不存在、教师范围、项目维护和复制、历史只读、课表幂等与确认、教师/实验室导入、报表计算和导出。真实AI模型/独立只读账号专项、外部Excel原件专项在缺少环境时跳过，不视为通过。

前端执行 npm run build（新Node使用 NODE_OPTIONS=--openssl-legacy-provider），修改页面执行 lint --no-fix。

2026-09-11 最终验证：开启数据库专项的 `mvn clean package` 成功，47项测试中45项通过、2项跳过（真实AI专项与外部Excel原件专项）；前端构建和修改页面lint通过。

检查时新库没有正式管理员账号；需按选定的数据初始化流程建立账号后才能实际登录。本次未导入种子、创建正式账号或重启现有服务。
