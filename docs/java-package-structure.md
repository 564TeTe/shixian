# Java 包结构

后端源码位于 `back/src/main/java/com`。按职责组织包，原 `teaching` 和 `auth` 混合包已拆分；同类职责集中存放。

```text
com
├── SpringbootSchemaApplication.java
├── controller           # HTTP 请求入口
│   ├── AccountController.java
│   ├── TeachingController.java
│   ├── TeachingImportController.java
│   ├── TeachingReportController.java
│   └── TeachingAiController.java
├── service              # 业务服务
│   ├── AccountService.java
│   ├── TeachingService.java
│   ├── TeachingTermService.java
│   ├── TeachingImportService.java
│   ├── TeachingReportService.java
│   └── TeachingAiService.java
├── model                # 共享数据对象
│   ├── AccountIdentity.java
│   ├── ExcelRow.java
│   └── response
│       └── ApiResponse.java
├── security             # 业务权限与 SQL 安全校验
│   ├── TeachingAccess.java
│   └── TeachingAiSqlGuard.java
├── exception            # 业务异常和统一响应处理
│   ├── AccessException.java
│   └── GlobalExceptionHandler.java
├── utils                # Excel、密码及下载工具
│   ├── TeachingExcel.java
│   ├── TeachingPasswords.java
│   └── ExcelDownloadUtils.java
├── interceptor
│   └── AuthorizationInterceptor.java
└── config
    └── InterceptorConfig.java
```

当前使用 JdbcTemplate，没有与数据库表一一映射的 ORM 实体。实际数据对象集中到 `model`，响应对象放 `model.response`。ExcelRow 从工具类内提取为独立对象，字段使用私有成员和访问方法。导入服务内部的私有 Candidate 是该算法的临时处理状态，继续保留为服务实现细节。

异常处理器从 TeachingController 提取为独立 Spring Advice，只作用于 `com.controller`。鉴权白名单改为控制器 Class 引用，避免移动包后仍匹配旧包名；SQL 白名单通过不可修改的集合视图提供给服务。模板和报表共用的下载响应提取到工具类，控制器之间不再调用下载方法。

测试按对应职责移到 `back/src/test/java/com/controller`、`service`、`security`、`utils`。服务内部的包可见方法仍由同包测试验证，不为迁移测试扩大业务方法的可见性。

本轮只整理 Java 代码位置和必要引用，保留 HTTP 路径、JSON 字段、SQL、事务、定时学期生成和原业务权限；不修改数据库结构或前端。

验证命令：在 back 目录设置 `$env:TEACHING_DB_TEST='1'`，执行 `mvn clean package`。HTTP 集成测试覆盖迁移后的教学、导入、报表、智能查询控制器，以及统一异常响应、下载、登录和密码重置。数据库测试的临时数据回滚；AI 专项及外部 Excel 原件专项依原有环境条件启用。

本次验证结果：47项测试中45项通过、2项条件跳过，构建成功；源码路径与包声明一致，jar中无旧teaching/auth包残留，`git diff --check`通过。
