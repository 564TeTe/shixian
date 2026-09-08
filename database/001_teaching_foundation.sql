-- MySQL 8.0.16+. Select the existing development database before running.
-- Additive migration. Never run T132.sql over an existing database.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS academic_year (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(9) NOT NULL UNIQUE,
  start_year SMALLINT NOT NULL UNIQUE,
  CONSTRAINT ck_year_start CHECK (start_year BETWEEN 1900 AND 9998),
  CONSTRAINT ck_year_name CHECK (name = CONCAT(start_year,'-',start_year+1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学年';

CREATE TABLE IF NOT EXISTS academic_term (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  academic_year_id BIGINT NOT NULL,
  term_no TINYINT NOT NULL,
  starts_on DATE NULL,
  ends_on DATE NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  UNIQUE KEY uk_term (academic_year_id, term_no),
  FOREIGN KEY (academic_year_id) REFERENCES academic_year(id),
  CONSTRAINT ck_term_no CHECK (term_no IN (1,2)),
  CONSTRAINT ck_term_status CHECK (status IN ('DRAFT','OPEN','ARCHIVED')),
  CONSTRAINT ck_term_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学期，状态与实际校历分别管理';

CREATE TABLE IF NOT EXISTS teaching_import_batch (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  file_sha256 CHAR(64) CHARACTER SET ascii NOT NULL UNIQUE,
  file_name VARCHAR(255) NOT NULL,
  parser_version VARCHAR(32) NOT NULL,
  row_count INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_import_rows CHECK (row_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课表导入批次，同一文件内容只暂存一次';

CREATE TABLE IF NOT EXISTS teaching_import_row (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id BIGINT NOT NULL,
  sheet_name VARCHAR(64) NOT NULL,
  source_row INT NOT NULL,
  raw_data JSON NOT NULL,
  parsed_schedule JSON NULL,
  issues JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'REVIEW',
  UNIQUE KEY uk_import_source (batch_id,sheet_name,source_row),
  FOREIGN KEY (batch_id) REFERENCES teaching_import_batch(id),
  CONSTRAINT ck_source_row CHECK (source_row > 1),
  CONSTRAINT ck_import_status CHECK (status IN ('REVIEW','ERROR','PROMOTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原始课表暂存，未确认身份和口径前不转正式任务';

CREATE TABLE IF NOT EXISTS course (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  course_code VARCHAR(64) NOT NULL UNIQUE,
  course_name VARCHAR(200) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨学期课程基础信息';

CREATE TABLE IF NOT EXISTS teaching_task (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_code VARCHAR(64) NOT NULL UNIQUE,
  term_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  source_import_row_id BIGINT NULL UNIQUE,
  course_name_snapshot VARCHAR(200) NOT NULL,
  department_name VARCHAR(200) NOT NULL,
  credits DECIMAL(6,2) NOT NULL,
  class_composition TEXT NOT NULL,
  major_composition TEXT NULL,
  class_size INT NOT NULL,
  enrollment_count INT NOT NULL,
  planned_lab_hours DECIMAL(8,2) NOT NULL,
  weekly_hours DECIMAL(6,2) NOT NULL,
  original_week_range VARCHAR(255) NOT NULL,
  scheduled_week_range TEXT NOT NULL,
  start_week SMALLINT NOT NULL,
  end_week SMALLINT NOT NULL,
  course_ends_at DATETIME NULL,
  course_weekly_hours_text VARCHAR(255) NOT NULL,
  teacher_names_original TEXT NOT NULL,
  locations_original TEXT NOT NULL,
  schedule_original TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY ix_task_term_course (term_id,course_id),
  FOREIGN KEY (term_id) REFERENCES academic_term(id),
  FOREIGN KEY (course_id) REFERENCES course(id),
  FOREIGN KEY (source_import_row_id) REFERENCES teaching_import_row(id),
  CONSTRAINT ck_task_counts CHECK (class_size >= 0 AND enrollment_count >= 0),
  CONSTRAINT ck_task_hours CHECK (credits >= 0 AND planned_lab_hours > 0 AND weekly_hours >= 0),
  CONSTRAINT ck_task_weeks CHECK (start_week >= 1 AND end_week >= start_week AND end_week <= 53)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学期开课任务，不以班级名称去重';

CREATE TABLE IF NOT EXISTS teaching_task_teacher (
  task_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  PRIMARY KEY (task_id,teacher_id),
  KEY ix_teacher_tasks (teacher_id,task_id),
  FOREIGN KEY (task_id) REFERENCES teaching_task(id),
  FOREIGN KEY (teacher_id) REFERENCES jiaoshi(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合授教师关联，姓名不是身份主键';

CREATE TABLE IF NOT EXISTS schedule_detail (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  lab_id BIGINT NOT NULL,
  teaching_week SMALLINT NOT NULL,
  weekday TINYINT NOT NULL,
  period_start SMALLINT NOT NULL,
  period_end SMALLINT NOT NULL,
  hours DECIMAL(6,2) NOT NULL,
  source_segment INT NOT NULL,
  UNIQUE KEY uk_schedule (task_id,lab_id,teaching_week,weekday,period_start,period_end),
  KEY ix_lab_task (lab_id,task_id),
  FOREIGN KEY (task_id) REFERENCES teaching_task(id),
  FOREIGN KEY (lab_id) REFERENCES shiyanshixinxi(id),
  CONSTRAINT ck_schedule_week CHECK (teaching_week BETWEEN 1 AND 53 AND weekday BETWEEN 1 AND 7),
  CONSTRAINT ck_schedule_period CHECK (period_start >= 1 AND period_end >= period_start AND period_end <= 24),
  CONSTRAINT ck_schedule_hours CHECK (hours > 0 AND source_segment >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按教学周和连续节次拆分的实验室排课';

CREATE TABLE IF NOT EXISTS experiment_project (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  project_code VARCHAR(64) NOT NULL UNIQUE,
  school_code VARCHAR(32) NOT NULL,
  name VARCHAR(50) NOT NULL,
  category_code CHAR(1) NOT NULL,
  type_code VARCHAR(2) NOT NULL,
  discipline_code VARCHAR(16) NOT NULL,
  requirement_code CHAR(1) NOT NULL,
  participant_type_code CHAR(1) NOT NULL,
  group_size SMALLINT NOT NULL,
  hours DECIMAL(6,2) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  copied_from_id BIGINT NULL,
  created_by_teacher_id BIGINT NULL,
  updated_by_teacher_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY ix_project_task (task_id,sort_order),
  FOREIGN KEY (task_id) REFERENCES teaching_task(id),
  FOREIGN KEY (copied_from_id) REFERENCES experiment_project(id),
  FOREIGN KEY (created_by_teacher_id) REFERENCES jiaoshi(id),
  FOREIGN KEY (updated_by_teacher_id) REFERENCES jiaoshi(id),
  CONSTRAINT ck_project_category CHECK (category_code IN ('1','2','3','4')),
  CONSTRAINT ck_project_type CHECK (type_code IN ('1','2','3','4','5')),
  CONSTRAINT ck_project_requirement CHECK (requirement_code IN ('1','2','3')),
  CONSTRAINT ck_project_participant CHECK (participant_type_code IN ('1','2','3','4','5')),
  CONSTRAINT ck_project_group CHECK (group_size BETWEEN 1 AND 99),
  CONSTRAINT ck_project_hours CHECK (hours > 0 AND hours <= 9999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学任务的实验项目版本，不自动导入模板示例';

-- MySQL CHECK cannot reference an AUTO_INCREMENT column. The application
-- must prevent self/cyclic copy references and enforce archived-term access.

-- MySQL does not support ADD COLUMN IF NOT EXISTS. Guard additive alterations.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='manager_teacher_id')=0,
 'ALTER TABLE shiyanshixinxi ADD COLUMN manager_teacher_id BIGINT NULL COMMENT ''负责人教师ID''', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='equipment_count')=0,
 'ALTER TABLE shiyanshixinxi ADD COLUMN equipment_count INT NULL COMMENT ''设备数，NULL代表未知''', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
-- Check constraints independently, so rerunning also recovers interrupted ALTERs.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='shiyanshixinxi' AND constraint_name='fk_lab_manager')=0,
 'ALTER TABLE shiyanshixinxi ADD CONSTRAINT fk_lab_manager FOREIGN KEY (manager_teacher_id) REFERENCES jiaoshi(id)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='shiyanshixinxi' AND constraint_name='ck_lab_equipment')=0,
 'ALTER TABLE shiyanshixinxi ADD CONSTRAINT ck_lab_equipment CHECK (equipment_count >= 0)', 'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
