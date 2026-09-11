-- 实验教学项目管理系统：精简版数据库结构，2026-09-11。
-- MySQL 8.0.16+；共11张表，统一英文命名、utf8mb4编码和中文注释。
-- 此文件用于删表重建，会删除所选数据库中下列业务表及其数据。
-- 执行顺序：USE t132; SOURCE T132.sql; SOURCE database/003_teaching_seed.sql;
-- 不要再执行旧版001、002、004迁移；旧后端须适配本结构后才能使用。
--
-- 核心关系：
-- academic_term 1:N teaching_task；course 1:N teaching_task。
-- account N:M teaching_task，通过teaching_task_teacher关联，支持多人合授。
-- teaching_task 1:N experiment_project；teaching_task 1:N schedule_detail。
-- laboratory 1:N schedule_detail；一项教学任务允许在多间实验室排课。
-- teaching_import_batch 1:N teaching_import_row；来源行最多对应一个正式任务。
-- account 1:N token；管理员和教师共用账号表，业务层校验角色权限。
--
-- 课程、学期、教师、班级相同也不自动视为同一个任务，任务编号单独唯一。
-- 教师姓名、地点和时间原文仅保存在导入行raw_data，正式查询使用关联表。
-- 教学班人数、学分、原始计划周次等源数据仍完整保留在raw_data中。
-- 项目属于具体教学任务，跨学期复制时生成独立记录，保留copied_from_id。
-- 项目关联实验室由所属任务排课推导，不表示该项目已在每间实验室实施。
-- 排课人时=按任务及实验室汇总的排课学时*任务选课人数，不乘合授教师数。

SET NAMES utf8mb4;
SET @simple_schema_old_foreign_key_checks = @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS
  `token`, `experiment_project`, `schedule_detail`, `teaching_task_teacher`,
  `teaching_task`, `teaching_import_row`, `teaching_import_batch`,
  `laboratory`, `shiyanshixinxi`, `teacher`, `jiaoshi`, `users`, `account`,
  `academic_term`, `academic_year`, `course`;

SET FOREIGN_KEY_CHECKS = @simple_schema_old_foreign_key_checks;

CREATE TABLE `account` /* 账号表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账号主键，管理员和教师统一编号',
  `username` varchar(64) NOT NULL COMMENT '唯一登录名，教师使用工号；TMP前缀表示待核实的临时工号',
  `password_hash` varchar(100) NOT NULL COMMENT '登录密码摘要，不保存明文密码',
  `display_name` varchar(100) NOT NULL COMMENT '显示姓名，教师账号填写教师姓名',
  `role` varchar(16) NOT NULL COMMENT '账号角色：ADMIN管理员，TEACHER教师',
  `college` varchar(100) DEFAULT NULL COMMENT '所属学院，未知或不适用时为空',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_username` (`username`),
  CONSTRAINT `ck_account_role` CHECK (`role` IN ('ADMIN','TEACHER')),
  CONSTRAINT `ck_account_name` CHECK (CHAR_LENGTH(TRIM(`username`)) > 0 AND CHAR_LENGTH(TRIM(`display_name`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号表：统一保存管理员和教师的登录信息';

CREATE TABLE `academic_term` /* 学年学期表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '学期主键',
  `start_year` smallint NOT NULL COMMENT '学年起始年份，如2025表示2025-2026学年',
  `term_no` tinyint NOT NULL COMMENT '学期序号：1第一学期，2第二学期',
  `starts_on` date DEFAULT NULL COMMENT '本学期开始日期，按实际校历维护',
  `ends_on` date DEFAULT NULL COMMENT '本学期结束日期，按实际校历维护',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿，OPEN开放，ARCHIVED归档；应用层禁止修改历史教学数据',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_term_year_number` (`start_year`,`term_no`),
  CONSTRAINT `ck_term_year` CHECK (`start_year` BETWEEN 1900 AND 9998),
  CONSTRAINT `ck_term_number` CHECK (`term_no` IN (1,2)),
  CONSTRAINT `ck_term_status` CHECK (`status` IN ('DRAFT','OPEN','ARCHIVED')),
  CONSTRAINT `ck_term_dates` CHECK (`starts_on` IS NULL OR `ends_on` IS NULL OR `ends_on` >= `starts_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学年学期表：保存学期时间和状态，学年名称由起始年份生成';

CREATE TABLE `course` /* 课程表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程主键',
  `course_code` varchar(64) NOT NULL COMMENT '课程号，按字符串保存；同名不同号的课程分别保存',
  `course_name` varchar(200) NOT NULL COMMENT '课程当前名称',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '课程创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_code` (`course_code`),
  CONSTRAINT `ck_course_text` CHECK (CHAR_LENGTH(TRIM(`course_code`)) > 0 AND CHAR_LENGTH(TRIM(`course_name`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表：保存课程号和名称，同一课程可以对应多次开课';

CREATE TABLE `laboratory` /* 实验室表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '实验室主键',
  `lab_code` varchar(32) NOT NULL COMMENT '实验室唯一编号，如36-401',
  `lab_name` varchar(100) NOT NULL COMMENT '实验室名称',
  `location` varchar(200) DEFAULT NULL COMMENT '实验室位置或地址',
  `manager_account_id` bigint DEFAULT NULL COMMENT '负责人账号ID，应用层限定为TEACHER角色；未知时为空',
  `equipment_count` int DEFAULT NULL COMMENT '设备数量，NULL表示未知，0表示已确认没有设备',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE可使用，INACTIVE停用',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lab_code` (`lab_code`),
  KEY `ix_lab_manager` (`manager_account_id`),
  CONSTRAINT `fk_lab_manager_account` FOREIGN KEY (`manager_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_lab_equipment` CHECK (`equipment_count` IS NULL OR `equipment_count` >= 0),
  CONSTRAINT `ck_lab_status` CHECK (`status` IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验室表：保存实验室资料，负责人不等同于授课教师';

CREATE TABLE `teaching_import_batch` /* 课表导入批次表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '导入批次主键',
  `file_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件内容SHA256摘要，防止同一文件重复导入',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `parser_version` varchar(32) NOT NULL COMMENT '解析器版本，便于核对历史解析结果',
  `row_count` int NOT NULL COMMENT '源文件有效数据行数，不是已成功转为教学任务的数量',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_file_hash` (`file_sha256`),
  CONSTRAINT `ck_import_row_count` CHECK (`row_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表导入批次表：记录导入文件，一个批次包含多条原始行';

CREATE TABLE `teaching_import_row` /* 课表导入原始行表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '原始导入行主键',
  `batch_id` bigint NOT NULL COMMENT '所属导入批次ID',
  `sheet_name` varchar(64) NOT NULL COMMENT '来源工作表名称',
  `source_row` int NOT NULL COMMENT '来源Excel实际行号，包含表头行',
  `raw_data` json NOT NULL COMMENT '完整原始行，保留课程、教师、班级人数、计划周次及时间地点原文等全部源字段',
  `issues` json NOT NULL COMMENT '解析问题和人工核对说明，无问题时保存空数组',
  `status` varchar(16) NOT NULL DEFAULT 'REVIEW' COMMENT '状态：REVIEW待核对，ERROR错误，PROMOTED已转正式任务',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_source_row` (`batch_id`,`sheet_name`,`source_row`),
  CONSTRAINT `fk_import_row_batch` FOREIGN KEY (`batch_id`) REFERENCES `teaching_import_batch` (`id`),
  CONSTRAINT `ck_import_source_row` CHECK (`source_row` > 1),
  CONSTRAINT `ck_import_row_status` CHECK (`status` IN ('REVIEW','ERROR','PROMOTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表导入原始行表：保存Excel原文和核对问题，不与正式业务字段重复维护';

CREATE TABLE `teaching_task` /* 教学任务表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '教学任务主键，一条记录代表一次独立开课',
  `task_code` varchar(64) NOT NULL COMMENT '独立且唯一的教学任务编号，不能仅由课程号和学期确定',
  `term_id` bigint NOT NULL COMMENT '所属学期ID',
  `course_id` bigint NOT NULL COMMENT '所属课程ID，同课程在同学期允许多个任务',
  `source_import_row_id` bigint DEFAULT NULL COMMENT '来源课表行ID，手工创建时为空；一行最多生成一个任务',
  `course_name_snapshot` varchar(200) NOT NULL COMMENT '开课时课程名称快照，防止课程更名影响历史报表',
  `class_composition` text NOT NULL COMMENT '授课班级组成，支持合班；不能作为唯一班级编号',
  `major_composition` text COMMENT '授课专业组成，来源缺失时为空，不根据班级名称猜测',
  `enrollment_count` int NOT NULL COMMENT '本教学任务选课人数，用于计算排课人时；不等同于原始班级人数或项目实际人数',
  `planned_lab_hours` decimal(8,2) NOT NULL COMMENT '计划实验总学时，实际排课学时从排课明细汇总，不覆盖此值',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  UNIQUE KEY `uk_task_source_row` (`source_import_row_id`),
  KEY `ix_task_term_course` (`term_id`,`course_id`),
  KEY `ix_task_course` (`course_id`),
  CONSTRAINT `fk_task_term` FOREIGN KEY (`term_id`) REFERENCES `academic_term` (`id`),
  CONSTRAINT `fk_task_course` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `fk_task_source_row` FOREIGN KEY (`source_import_row_id`) REFERENCES `teaching_import_row` (`id`),
  CONSTRAINT `ck_task_enrollment` CHECK (`enrollment_count` >= 0),
  CONSTRAINT `ck_task_planned_hours` CHECK (`planned_lab_hours` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教学任务表：某学期面向某些班级的一次开课，教师通过关联表指定';

CREATE TABLE `teaching_task_teacher` /* 任务教师关联表 */ (
  `task_id` bigint NOT NULL COMMENT '教学任务ID',
  `teacher_account_id` bigint NOT NULL COMMENT '授课教师账号ID，应用层限定account.role为TEACHER',
  PRIMARY KEY (`task_id`,`teacher_account_id`),
  KEY `ix_teacher_tasks` (`teacher_account_id`,`task_id`),
  CONSTRAINT `fk_assignment_task` FOREIGN KEY (`task_id`) REFERENCES `teaching_task` (`id`),
  CONSTRAINT `fk_assignment_account` FOREIGN KEY (`teacher_account_id`) REFERENCES `account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务教师关联表：连接教学任务与教师，支持一师多课和多人合授';

CREATE TABLE `schedule_detail` /* 排课明细表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '排课明细主键',
  `task_id` bigint NOT NULL COMMENT '所属教学任务ID',
  `lab_id` bigint NOT NULL COMMENT '本次上课的实验室ID',
  `teaching_week` smallint NOT NULL COMMENT '教学周次，单周或双周课表展开为具体周次',
  `weekday` tinyint NOT NULL COMMENT '星期：1周一至7周日',
  `period_start` smallint NOT NULL COMMENT '连续上课时段的开始节次',
  `period_end` smallint NOT NULL COMMENT '连续上课时段的结束节次',
  `hours` smallint GENERATED ALWAYS AS (`period_end` - `period_start` + 1) VIRTUAL COMMENT '排课学时，按每节1学时自动计算，不允许单独填写',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_slot` (`task_id`,`lab_id`,`teaching_week`,`weekday`,`period_start`,`period_end`),
  KEY `ix_schedule_lab_task` (`lab_id`,`task_id`),
  CONSTRAINT `fk_schedule_task` FOREIGN KEY (`task_id`) REFERENCES `teaching_task` (`id`),
  CONSTRAINT `fk_schedule_lab` FOREIGN KEY (`lab_id`) REFERENCES `laboratory` (`id`),
  CONSTRAINT `ck_schedule_week` CHECK (`teaching_week` BETWEEN 1 AND 53),
  CONSTRAINT `ck_schedule_weekday` CHECK (`weekday` BETWEEN 1 AND 7),
  CONSTRAINT `ck_schedule_period` CHECK (`period_start` >= 1 AND `period_end` >= `period_start` AND `period_end` <= 24)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排课明细表：记录任务的实验室、周次、星期和节次，跨任务占用冲突由业务层核对';

CREATE TABLE `experiment_project` /* 实验项目表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '实验项目主键，每次开课单独维护项目版本',
  `task_id` bigint NOT NULL COMMENT '所属教学任务ID，不直接按课程号共享可编辑项目',
  `project_code` varchar(64) NOT NULL COMMENT '实验编号，系统生成且全局唯一；复制项目时生成新编号',
  `school_code` char(5) NOT NULL DEFAULT '11059' COMMENT '学校代码，本校默认11059，保留采集表导出所需代码',
  `name` varchar(50) NOT NULL COMMENT '实验项目名称',
  `category_code` char(1) NOT NULL COMMENT '实验类别：1基础，2专业基础，3专业，4其他',
  `type_code` varchar(2) NOT NULL COMMENT '实验类型：1演示性，2验证性，3综合性，4设计研究，5其他',
  `discipline_code` varchar(16) NOT NULL COMMENT '实验所属学科代码，保留0809等代码的前导零',
  `requirement_code` char(1) NOT NULL COMMENT '实验要求：1必做，2选做，3其他',
  `participant_type_code` char(1) NOT NULL COMMENT '实验者类别：1博士生，2硕士生，3本科生，4专科生，5其他',
  `group_size` smallint NOT NULL COMMENT '每组同时完成实验的人数，不是总参与人数',
  `hours` decimal(6,2) NOT NULL COMMENT '完成本实验项目的学时数，不是课程总学时',
  `participant_count` int DEFAULT NULL COMMENT '已确认的本项目参与人数，未知时为空；按选课人数推算的值不可冒充实际值',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '项目显示顺序',
  `copied_from_id` bigint DEFAULT NULL COMMENT '复制来源项目ID，非复制创建时为空',
  `created_by_account_id` bigint DEFAULT NULL COMMENT '创建人账号ID，可以是管理员或授课教师',
  `updated_by_account_id` bigint DEFAULT NULL COMMENT '最后修改人账号ID，可以是管理员或授课教师',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  KEY `ix_project_task_order` (`task_id`,`sort_order`),
  KEY `ix_project_source` (`copied_from_id`),
  KEY `ix_project_creator` (`created_by_account_id`),
  KEY `ix_project_editor` (`updated_by_account_id`),
  CONSTRAINT `fk_project_task` FOREIGN KEY (`task_id`) REFERENCES `teaching_task` (`id`),
  CONSTRAINT `fk_project_source` FOREIGN KEY (`copied_from_id`) REFERENCES `experiment_project` (`id`),
  CONSTRAINT `fk_project_creator` FOREIGN KEY (`created_by_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `fk_project_editor` FOREIGN KEY (`updated_by_account_id`) REFERENCES `account` (`id`),
  CONSTRAINT `ck_project_category` CHECK (`category_code` IN ('1','2','3','4')),
  CONSTRAINT `ck_project_type` CHECK (`type_code` IN ('1','2','3','4','5')),
  CONSTRAINT `ck_project_requirement` CHECK (`requirement_code` IN ('1','2','3')),
  CONSTRAINT `ck_project_participant_type` CHECK (`participant_type_code` IN ('1','2','3','4','5')),
  CONSTRAINT `ck_project_group` CHECK (`group_size` BETWEEN 1 AND 99),
  CONSTRAINT `ck_project_hours` CHECK (`hours` > 0 AND `hours` <= 9999),
  CONSTRAINT `ck_project_participants` CHECK (`participant_count` IS NULL OR `participant_count` >= 0),
  CONSTRAINT `ck_project_order` CHECK (`sort_order` >= 0),
  CONSTRAINT `ck_project_name` CHECK (CHAR_LENGTH(TRIM(`name`)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验项目表：保存某教学任务的实验内容，课程、学期和教师通过任务关联获取';

CREATE TABLE `token` /* 登录令牌表 */ (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '登录凭证主键',
  `account_id` bigint NOT NULL COMMENT '登录账号ID，直接关联统一账号表',
  `token` varchar(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '区分大小写的随机登录令牌，不是账号密码',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '令牌签发时间',
  `expires_at` datetime NOT NULL COMMENT '令牌过期时间，由签发程序指定',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_value` (`token`),
  KEY `ix_token_account` (`account_id`),
  KEY `ix_token_expiry` (`expires_at`),
  CONSTRAINT `fk_token_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录令牌表：保存账号的登录凭证和过期时间，姓名和角色从账号表读取';
