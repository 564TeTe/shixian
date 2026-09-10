-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: t132
-- ------------------------------------------------------
-- Server version	8.0.41

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `academic_term`
--

DROP TABLE IF EXISTS `academic_term`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `academic_term` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `academic_year_id` bigint NOT NULL COMMENT '所属学年ID',
  `term_no` tinyint NOT NULL COMMENT '学期序号：1第一学期，2第二学期',
  `starts_on` date DEFAULT NULL COMMENT '学期开始日期',
  `ends_on` date DEFAULT NULL COMMENT '学期结束日期',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '学期状态：DRAFT草稿，OPEN开放，ARCHIVED归档',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_term` (`academic_year_id`,`term_no`),
  CONSTRAINT `academic_term_ibfk_1` FOREIGN KEY (`academic_year_id`) REFERENCES `academic_year` (`id`),
  CONSTRAINT `ck_term_dates` CHECK (((`ends_on` is null) or (`starts_on` is null) or (`ends_on` >= `starts_on`))),
  CONSTRAINT `ck_term_no` CHECK ((`term_no` in (1,2))),
  CONSTRAINT `ck_term_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'OPEN',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学期，状态与实际校历分别管理';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `academic_year`
--

DROP TABLE IF EXISTS `academic_year`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `academic_year` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(9) NOT NULL COMMENT '学年名称，例如2026-2027',
  `start_year` smallint NOT NULL COMMENT '学年起始年份',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  UNIQUE KEY `start_year` (`start_year`),
  CONSTRAINT `ck_year_name` CHECK ((`name` = concat(`start_year`,_utf8mb4'-',(`start_year` + 1)))),
  CONSTRAINT `ck_year_start` CHECK ((`start_year` between 1900 and 9998))
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学年';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course`
--

DROP TABLE IF EXISTS `course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `course_code` varchar(64) NOT NULL COMMENT '课程编号',
  `course_name` varchar(200) NOT NULL COMMENT '课程名称',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `course_code` (`course_code`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨学期课程基础信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `experiment_project`
--

DROP TABLE IF EXISTS `experiment_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `experiment_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '所属教学任务ID',
  `project_code` varchar(64) NOT NULL COMMENT '实验项目编号',
  `school_code` varchar(32) NOT NULL COMMENT '学校代码',
  `name` varchar(50) NOT NULL COMMENT '实验项目名称',
  `category_code` char(1) NOT NULL COMMENT '实验类别代码：1基础，2专业基础，3专业，4其他',
  `type_code` varchar(2) NOT NULL COMMENT '实验类型代码：1演示，2验证，3综合，4设计研究，5其他',
  `discipline_code` varchar(16) NOT NULL COMMENT '所属学科代码',
  `requirement_code` char(1) NOT NULL COMMENT '实验要求代码：1必修，2选修，3其他',
  `participant_type_code` char(1) NOT NULL COMMENT '实验者类别代码：1博士生，2硕士生，3本科生，4专科生，5其他',
  `group_size` smallint NOT NULL COMMENT '每组实验人数',
  `hours` decimal(6,2) NOT NULL COMMENT '实验学时',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `copied_from_id` bigint DEFAULT NULL COMMENT '复制来源实验项目ID',
  `created_by_teacher_id` bigint DEFAULT NULL COMMENT '创建人教师ID，管理员创建时为空',
  `updated_by_teacher_id` bigint DEFAULT NULL COMMENT '最后修改人教师ID，管理员修改时为空',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `project_code` (`project_code`),
  KEY `ix_project_task` (`task_id`,`sort_order`),
  KEY `copied_from_id` (`copied_from_id`),
  KEY `created_by_teacher_id` (`created_by_teacher_id`),
  KEY `updated_by_teacher_id` (`updated_by_teacher_id`),
  CONSTRAINT `experiment_project_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `teaching_task` (`id`),
  CONSTRAINT `experiment_project_ibfk_2` FOREIGN KEY (`copied_from_id`) REFERENCES `experiment_project` (`id`),
  CONSTRAINT `experiment_project_ibfk_3` FOREIGN KEY (`created_by_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `experiment_project_ibfk_4` FOREIGN KEY (`updated_by_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `ck_project_category` CHECK ((`category_code` in (_utf8mb4'1',_utf8mb4'2',_utf8mb4'3',_utf8mb4'4'))),
  CONSTRAINT `ck_project_group` CHECK ((`group_size` between 1 and 99)),
  CONSTRAINT `ck_project_hours` CHECK (((`hours` > 0) and (`hours` <= 9999))),
  CONSTRAINT `ck_project_participant` CHECK ((`participant_type_code` in (_utf8mb4'1',_utf8mb4'2',_utf8mb4'3',_utf8mb4'4',_utf8mb4'5'))),
  CONSTRAINT `ck_project_requirement` CHECK ((`requirement_code` in (_utf8mb4'1',_utf8mb4'2',_utf8mb4'3'))),
  CONSTRAINT `ck_project_type` CHECK ((`type_code` in (_utf8mb4'1',_utf8mb4'2',_utf8mb4'3',_utf8mb4'4',_utf8mb4'5')))
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教学任务的实验项目版本，不自动导入模板示例';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teacher`
--

DROP TABLE IF EXISTS `teacher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `teacher_no` varchar(200) NOT NULL COMMENT '工号',
  `password` varchar(200) NOT NULL COMMENT '登录密码摘要',
  `teacher_name` varchar(200) DEFAULT NULL COMMENT '教师姓名',
  `gender` varchar(200) DEFAULT NULL COMMENT '性别',
  `avatar_url` varchar(200) DEFAULT NULL COMMENT '头像',
  `college` varchar(200) DEFAULT NULL COMMENT '学院',
  `job_title` varchar(200) DEFAULT NULL COMMENT '职称',
  `phone` varchar(200) DEFAULT NULL COMMENT '电话',
  PRIMARY KEY (`id`),
  UNIQUE KEY `teacher_no` (`teacher_no`)
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb3 COMMENT='教师';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schedule_detail`
--

DROP TABLE IF EXISTS `schedule_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '所属教学任务ID',
  `lab_id` bigint NOT NULL COMMENT '实验室ID',
  `teaching_week` smallint NOT NULL COMMENT '教学周次',
  `weekday` tinyint NOT NULL COMMENT '星期：1周一至7周日',
  `period_start` smallint NOT NULL COMMENT '开始节次',
  `period_end` smallint NOT NULL COMMENT '结束节次',
  `hours` decimal(6,2) NOT NULL COMMENT '实验学时',
  `source_segment` int NOT NULL COMMENT '原始排课片段序号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule` (`task_id`,`lab_id`,`teaching_week`,`weekday`,`period_start`,`period_end`),
  KEY `ix_lab_task` (`lab_id`,`task_id`),
  CONSTRAINT `schedule_detail_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `teaching_task` (`id`),
  CONSTRAINT `schedule_detail_ibfk_2` FOREIGN KEY (`lab_id`) REFERENCES `laboratory` (`id`),
  CONSTRAINT `ck_schedule_hours` CHECK (((`hours` > 0) and (`source_segment` >= 1))),
  CONSTRAINT `ck_schedule_period` CHECK (((`period_start` >= 1) and (`period_end` >= `period_start`) and (`period_end` <= 24))),
  CONSTRAINT `ck_schedule_week` CHECK (((`teaching_week` between 1 and 53) and (`weekday` between 1 and 7)))
) ENGINE=InnoDB AUTO_INCREMENT=2221 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按教学周和连续节次拆分的实验室排课';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `laboratory`
--

DROP TABLE IF EXISTS `laboratory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `laboratory` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `lab_code` varchar(200) NOT NULL COMMENT '实验室编号',
  `lab_name` varchar(200) NOT NULL COMMENT '实验室名称',
  `lab_size` varchar(200) NOT NULL COMMENT '实验室规模',
  `image_url` varchar(200) DEFAULT NULL COMMENT '实验室图片',
  `location` varchar(200) DEFAULT NULL COMMENT '实验室位置',
  `available_hours` varchar(200) DEFAULT NULL COMMENT '可约时间',
  `description` longtext COMMENT '实验室详情',
  `status` varchar(200) NOT NULL COMMENT '实验室状态',
  `manager_teacher_id` bigint DEFAULT NULL COMMENT '负责人教师ID',
  `equipment_count` int DEFAULT NULL COMMENT '设备数，NULL代表未知',
  PRIMARY KEY (`id`),
  UNIQUE KEY `lab_code` (`lab_code`),
  KEY `fk_lab_manager` (`manager_teacher_id`),
  CONSTRAINT `fk_lab_manager` FOREIGN KEY (`manager_teacher_id`) REFERENCES `teacher` (`id`),
  CONSTRAINT `ck_lab_equipment` CHECK ((`equipment_count` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=98 DEFAULT CHARSET=utf8mb3 COMMENT='实验室信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teaching_import_batch`
--

DROP TABLE IF EXISTS `teaching_import_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teaching_import_batch` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_sha256` char(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '导入文件SHA256摘要，用于重复导入校验',
  `file_name` varchar(255) NOT NULL COMMENT '导入文件名',
  `parser_version` varchar(32) NOT NULL COMMENT '课表解析器版本',
  `row_count` int NOT NULL COMMENT '导入数据行数',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `file_sha256` (`file_sha256`),
  CONSTRAINT `ck_import_rows` CHECK ((`row_count` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课表导入批次，同一文件内容只暂存一次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teaching_import_row`
--

DROP TABLE IF EXISTS `teaching_import_row`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teaching_import_row` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `batch_id` bigint NOT NULL COMMENT '所属导入批次ID',
  `sheet_name` varchar(64) NOT NULL COMMENT '来源工作表名称',
  `source_row` int NOT NULL COMMENT '来源Excel行号',
  `raw_data` json NOT NULL COMMENT '原始行数据JSON',
  `parsed_schedule` json DEFAULT NULL COMMENT '解析后的排课数据JSON',
  `issues` json NOT NULL COMMENT '解析问题与待核对事项JSON',
  `status` varchar(16) NOT NULL DEFAULT 'REVIEW' COMMENT '导入行状态：REVIEW待核对，ERROR错误，PROMOTED已转教学任务',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_import_source` (`batch_id`,`sheet_name`,`source_row`),
  CONSTRAINT `teaching_import_row_ibfk_1` FOREIGN KEY (`batch_id`) REFERENCES `teaching_import_batch` (`id`),
  CONSTRAINT `ck_import_status` CHECK ((`status` in (_utf8mb4'REVIEW',_utf8mb4'ERROR',_utf8mb4'PROMOTED'))),
  CONSTRAINT `ck_source_row` CHECK ((`source_row` > 1))
) ENGINE=InnoDB AUTO_INCREMENT=517 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='原始课表暂存，未确认身份和口径前不转正式任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teaching_task`
--

DROP TABLE IF EXISTS `teaching_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teaching_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_code` varchar(64) NOT NULL COMMENT '教学任务编号',
  `term_id` bigint NOT NULL COMMENT '所属学期ID',
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `source_import_row_id` bigint DEFAULT NULL COMMENT '来源导入行ID，手工创建时为空',
  `course_name_snapshot` varchar(200) NOT NULL COMMENT '开课时的课程名称快照',
  `department_name` varchar(200) NOT NULL COMMENT '开课学院名称',
  `credits` decimal(6,2) NOT NULL COMMENT '课程学分',
  `class_composition` text NOT NULL COMMENT '授课班级组成',
  `major_composition` text COMMENT '授课专业组成',
  `class_size` int NOT NULL COMMENT '班级人数',
  `enrollment_count` int NOT NULL COMMENT '选课人数',
  `planned_lab_hours` decimal(8,2) NOT NULL COMMENT '计划实验总学时',
  `weekly_hours` decimal(6,2) NOT NULL COMMENT '每周实验学时',
  `original_week_range` varchar(255) NOT NULL COMMENT '原始课表周次范围',
  `scheduled_week_range` text NOT NULL COMMENT '实际排课周次范围',
  `start_week` smallint NOT NULL COMMENT '开始教学周',
  `end_week` smallint NOT NULL COMMENT '结束教学周',
  `course_ends_at` datetime DEFAULT NULL COMMENT '课程结束时间',
  `course_weekly_hours_text` varchar(255) NOT NULL COMMENT '原始课程周学时文本',
  `teacher_names_original` text NOT NULL COMMENT '原始授课教师姓名文本',
  `locations_original` text NOT NULL COMMENT '原始上课地点文本',
  `schedule_original` text NOT NULL COMMENT '原始排课时间文本',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `default_lab_id` bigint DEFAULT NULL COMMENT '默认实验室ID，用于尚未排课的任务',
  PRIMARY KEY (`id`),
  UNIQUE KEY `task_code` (`task_code`),
  UNIQUE KEY `source_import_row_id` (`source_import_row_id`),
  KEY `ix_task_term_course` (`term_id`,`course_id`),
  KEY `course_id` (`course_id`),
  KEY `fk_task_default_lab` (`default_lab_id`),
  CONSTRAINT `fk_task_default_lab` FOREIGN KEY (`default_lab_id`) REFERENCES `laboratory` (`id`),
  CONSTRAINT `teaching_task_ibfk_1` FOREIGN KEY (`term_id`) REFERENCES `academic_term` (`id`),
  CONSTRAINT `teaching_task_ibfk_2` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `teaching_task_ibfk_3` FOREIGN KEY (`source_import_row_id`) REFERENCES `teaching_import_row` (`id`),
  CONSTRAINT `ck_task_counts` CHECK (((`class_size` >= 0) and (`enrollment_count` >= 0))),
  CONSTRAINT `ck_task_hours` CHECK (((`credits` >= 0) and (`planned_lab_hours` > 0) and (`weekly_hours` >= 0))),
  CONSTRAINT `ck_task_weeks` CHECK (((`start_week` >= 1) and (`end_week` >= `start_week`) and (`end_week` <= 53)))
) ENGINE=InnoDB AUTO_INCREMENT=286 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学期开课任务，不以班级名称去重';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `teaching_task_teacher`
--

DROP TABLE IF EXISTS `teaching_task_teacher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teaching_task_teacher` (
  `task_id` bigint NOT NULL COMMENT '所属教学任务ID',
  `teacher_id` bigint NOT NULL COMMENT '授课教师ID',
  PRIMARY KEY (`task_id`,`teacher_id`),
  KEY `ix_teacher_tasks` (`teacher_id`,`task_id`),
  CONSTRAINT `teaching_task_teacher_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `teaching_task` (`id`),
  CONSTRAINT `teaching_task_teacher_ibfk_2` FOREIGN KEY (`teacher_id`) REFERENCES `teacher` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合授教师关联，姓名不是身份主键';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `token`
--

DROP TABLE IF EXISTS `token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `userid` bigint NOT NULL COMMENT '用户id',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `tablename` varchar(100) DEFAULT NULL COMMENT '表名',
  `role` varchar(100) DEFAULT NULL COMMENT '角色',
  `token` varchar(200) NOT NULL COMMENT '登录令牌',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '新增时间',
  `expiratedtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '过期时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb3 COMMENT='登录令牌表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '登录密码摘要',
  `role` varchar(100) DEFAULT '管理员' COMMENT '角色',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '新增时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb3 COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping routines for database 't132'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-10  8:38:24
