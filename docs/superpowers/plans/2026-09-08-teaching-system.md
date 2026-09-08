# 实验教学系统可用版实施计划

用户要求：清理原仓库全部演示数据，完成数据库对应的实际业务功能和页面，而不是只交付表结构。本次沿用 Spring Boot/Vue2 框架，按已讨论的教学管理流程实现。

## 交付范围

1. 备份并清空旧预约、学生、设备、采购、维修、公告、收藏、评论、配置与旧课程演示数据；重新初始化管理员、课表教师、实验室、学年学期和正式教学任务。保留老师提供的原始课表和问题记录。临时教师账号明确标注，未知负责人/设备数不虚构。
2. 教学概览、学期、课程与课表、实验项目、实验室、教师账号、导入中心、统计报表、自然语言查询页面；管理员和教师两种角色。
3. 教师只能访问自己参与的教学任务与项目，历史学期项目只读，当前任务支持项目增删改、Excel导入与跨学期复制。
4. Excel课表上传/校验/生成业务数据，教师/实验室导入，项目导入模板与导出；报表按实际排课学时和选课人数展示人时，明确计划/排课差异，不伪造实验项目参与人数。
5. AI查询提供模型配置状态和提示示例；配置后以允许的业务表元数据生成SELECT，解析校验、限行限时；教师数据范围必须强制限制。无模型配置时明确显示未配置，不伪装成功。
6. 完整构建、数据库/接口/权限测试和真实浏览器操作；运行本地前后端供用户使用。

## 接口约定

统一前缀 `/teaching`，沿用 Token 请求头与响应 `{code:0,data:...}`。业务错误使用非0 code + msg。所有列名以数据库 snake_case 返回，查询参数/表单 DTO 使用下面约定。

公共列表：`GET /lookups` → `{terms:[{id,name,status}],teachers:[{id,gonghao,jiaoshixingming}],labs:[{id,shiyanshibianhao,shiyanshimingcheng}],courses:[{id,course_code,course_name}]}`。教师返回范围受限。

核心接口：
- `GET /dashboard` → counts `{tasks,courses,labs,teachers,projects,imports}`、currentTerm、warnings。
- `GET /terms` → 数组；`POST /terms/generate` → 自动补齐当前学期（管理员）。
- `GET /tasks?termId=&q=&page=1&limit=20` → `{list,total}`，含 id/task_code/course_code/course_name/term_name/teacher_names/class_composition/enrollment_count/planned_lab_hours/scheduled_hours/lab_names/project_count/status。
- `GET /tasks/{id}` → 任务对象 + `schedule` 数组。
- `POST /tasks` 管理员创建任务 `{termId,courseId,teacherIds,labId,classComposition,enrollmentCount,plannedLabHours,majorComposition}`。
- `GET /projects?taskId=` → `{list,total,editable}`；`POST /projects`、`PUT /projects/{id}`，项目字段用数据库 snake_case（含task_id）；`DELETE /projects/{id}`。
- `POST /projects/copy` → `{sourceTaskId,targetTaskId}`，目标必须当前可写、源有访问权限。
- `GET /labs?q=` → `{list,total}`；`POST /labs`、`PUT /labs/{id}` 表单 snake_case shiyanshibianhao/shiyanshimingcheng/shiyanshiweizhi/manager_teacher_id/equipment_count。
- `GET /teachers?q=` → `{list,total}`；`POST /teachers`、`PUT /teachers/{id}` snake_case gonghao/jiaoshixingming/xueyuan；`POST /teachers/{id}/reset-password` → `{password}` 仅管理员。
- `POST /account/password` → `{oldPassword,newPassword}`。

导入/报表/AI接口：
- `GET /imports` → `{list,total}`；`GET /imports/{id}` → `{batch,rows:[{source_row,issues,status,...}],summary}`。
- `POST /imports/timetable` multipart file；自动保存批次与合法任务（疑点保留），返回 `{batchId,rows,promoted,errors,warnings}`。同文件幂等；异文件疑似旧任务只暂存警告，不自动覆盖。
- `POST /imports/projects?taskId=`、`POST /imports/teachers`、`POST /imports/labs` multipart file；返回 `{imported,warnings}`。必须实际xlsx，采用Apache POI。
- `GET /templates/{projects|teachers|labs|timetable}` 下载 xlsx。
- `GET /reports?yearId=&termId=` → `{labs:[{lab_code,lab_name,course_count,task_count,scheduled_hours,person_hours}],projects:[{term_name,course_code,course_name,lab_names,project_code,project_name,hours,enrollment_count}],basis,warnings}`；`GET /reports/export?yearId=&termId=&type=labs|projects` xlsx。
- `GET /ai/status` → `{configured,model,examples}`；`POST /ai/query` → `{question}` 返回 `{sql,columns,rows,truncated}`。

## 分工与边界

- 主代理：数据备份/清理/正式初始化、认证与旧接口关闭、pom依赖、数据库增量迁移、启动/集成验证、文档。
- 核心后端：`com.teaching` 中 TeachingAccess、TeachingController、TeachingService、TeachingTermService 及自身测试；不改pom/旧认证/数据库脚本。TeachingAccess约定 requireAdmin(request)、teacherId(request)（管理员返回null）、requireTask(request,long,boolean)；公共教师密码使用主代理提供 TeachingPasswords.hash/matches/newPassword。
- 导入报表后端：同包独立文件 `TeachingImport*`、`TeachingReport*`、`TeachingAi*`、`TeachingExcel*`；不改核心文件/pom/数据库，依赖TeachingAccess和TeachingPasswords。
- 前端：`front/src/views/teaching/**`、home/login、router/utils/menu/base、布局header/sidebar/main与相关样式，尊重已有未提交蓝色主题修改；不改后端和数据库。

所有实现者共享工作区，不回退他人修改，不自行提交推送。实现结束做接口契约和代码审阅，再运行真实登录/页面/导入/项目/权限测试。用户本轮已要求实现完整可用流程，沿用既定设计并自行处理常规实现选择。
