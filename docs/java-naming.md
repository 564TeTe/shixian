# Java命名与兼容边界

本次以新11表结构和实际教学前端为准，之前为旧模板建立的英文领域模块已随无用后端清理，不保留无调用实体与Mapper。最终范围见 [java-cleanup.md](java-cleanup.md)。

控制器、服务、数据对象、安全校验、异常及工具类已按职责集中到对应包，详见 [当前 Java 包结构](java-package-structure.md)。

| 职责 | 当前名称 |
| --- | --- |
| 登录和令牌 | AccountController、AccountService |
| 认证身份 | AccountIdentity；属性 accountId、username、role |
| 统一返回 | ApiResponse，替代含义不明确的 R |
| 教学业务 | TeachingService、TeachingAccess、TeachingTermService |
| 导入、报表、智能查询 | TeachingImportService、TeachingReportService、TeachingAiService |

类用UpperCamelCase，方法和变量用lowerCamelCase，常量用大写下划线。Java不新增拼音标识符。保留项目启动类与jar名，避免影响启动脚本。

以下字符串是实际Vue前端的外部协议，不是Java类名或变量名：

- /jiaoshi/login、/users/login及退出路径。
- gonghao、jiaoshixingming、xueyuan、shiyanshibianhao、shiyanshimingcheng、shiyanshiweizhi等JSON键，由英文SQL列映射。
- manager_teacher_id对应manager_account_id；teacher_ids对应任务关联账号ID。
- Session的users/teacher兼容角色范围逻辑，实际查询account表并校验数据库角色。
- academic_year_id的值为起始年份，不再表示已删除表的主键。

不重写用户数据库连接和SQL建表脚本。以后统一公开API字段时须同时迁移前端和契约测试，不能只修改Java字符串。
