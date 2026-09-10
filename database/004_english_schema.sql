-- MySQL 8.0.16+. Run once after the old T132.sql + 003 seed, or after 001 + 002.
-- Stop the backend and back up the database first. MySQL DDL commits implicitly.
-- Rerunnable; preserves rows, primary keys, foreign keys, defaults and constraints.
-- Fresh installations already use English names and only need T132.sql + 003.
SET NAMES utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_english_schema_004$$
CREATE PROCEDURE migrate_english_schema_004()
BEGIN
  IF DATABASE() IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the teaching database with USE first';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='academic_term')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: academic_term';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_term' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_term.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_term' AND column_name='academic_year_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_term.academic_year_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_term' AND column_name='term_no')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_term.term_no';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_term' AND column_name='starts_on')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_term.starts_on';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_term' AND column_name='ends_on')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_term.ends_on';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_term' AND column_name='status')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_term.status';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='academic_year')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: academic_year';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_year' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_year.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_year' AND column_name='name')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_year.name';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='academic_year' AND column_name='start_year')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: academic_year.start_year';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='course')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: course';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: course.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='course_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: course.course_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='course_name')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: course.course_name';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='course' AND column_name='created_at')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: course.created_at';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='experiment_project')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: experiment_project';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='task_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.task_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='project_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.project_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='school_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.school_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='name')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.name';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='category_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.category_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='type_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.type_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='discipline_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.discipline_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='requirement_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.requirement_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='participant_type_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.participant_type_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='group_size')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.group_size';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='hours')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.hours';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='sort_order')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.sort_order';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='copied_from_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.copied_from_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='created_by_teacher_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.created_by_teacher_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='updated_by_teacher_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.updated_by_teacher_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='created_at')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.created_at';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='experiment_project' AND column_name='updated_at')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: experiment_project.updated_at';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='jiaoshi')+(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='teacher')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: jiaoshi / teacher';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='id')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='addtime')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='addtime')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.addtime';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='gonghao')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='teacher_no')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='gonghao')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='teacher_no')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.teacher_no';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='mima')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='password')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='mima')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='password')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.password';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='jiaoshixingming')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='teacher_name')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='jiaoshixingming')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='teacher_name')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.teacher_name';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='xingbie')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='gender')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='xingbie')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='gender')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.gender';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='touxiang')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='avatar_url')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='touxiang')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='avatar_url')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.avatar_url';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='xueyuan')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='college')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='xueyuan')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='college')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.college';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='zhicheng')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='job_title')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='zhicheng')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='job_title')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.job_title';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='dianhua')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jiaoshi' AND column_name='phone')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='dianhua')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='phone')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teacher.phone';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='schedule_detail')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: schedule_detail';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='task_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.task_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='lab_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.lab_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='teaching_week')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.teaching_week';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='weekday')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.weekday';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='period_start')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.period_start';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='period_end')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.period_end';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='hours')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.hours';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedule_detail' AND column_name='source_segment')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: schedule_detail.source_segment';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi')+(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='laboratory')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: shiyanshixinxi / laboratory';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='id')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='addtime')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='addtime')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.addtime';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='shiyanshibianhao')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='lab_code')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshibianhao')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='lab_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.lab_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='shiyanshimingcheng')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='lab_name')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshimingcheng')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='lab_name')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.lab_name';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='shiyanshiguimo')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='lab_size')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshiguimo')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='lab_size')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.lab_size';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='shiyanshitupian')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='image_url')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshitupian')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='image_url')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.image_url';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='shiyanshiweizhi')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='location')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshiweizhi')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='location')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.location';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='keyueshijian')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='available_hours')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='keyueshijian')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='available_hours')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.available_hours';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='shiyanshixiangqing')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='description')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshixiangqing')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='description')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.description';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='shiyanshizhuangtai')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='status')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshizhuangtai')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='status')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.status';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='manager_teacher_id')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='manager_teacher_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.manager_teacher_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi' AND column_name='equipment_count')+(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='equipment_count')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: laboratory.equipment_count';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='teaching_import_batch')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: teaching_import_batch';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_batch' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_batch.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_batch' AND column_name='file_sha256')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_batch.file_sha256';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_batch' AND column_name='file_name')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_batch.file_name';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_batch' AND column_name='parser_version')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_batch.parser_version';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_batch' AND column_name='row_count')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_batch.row_count';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_batch' AND column_name='created_at')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_batch.created_at';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='teaching_import_row')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: teaching_import_row';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_row' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_row.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_row' AND column_name='batch_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_row.batch_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_row' AND column_name='sheet_name')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_row.sheet_name';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_row' AND column_name='source_row')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_row.source_row';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_row' AND column_name='raw_data')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_row.raw_data';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_row' AND column_name='parsed_schedule')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_row.parsed_schedule';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_row' AND column_name='issues')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_row.issues';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_import_row' AND column_name='status')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_import_row.status';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='teaching_task')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: teaching_task';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='task_code')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.task_code';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='term_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.term_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='course_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.course_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='source_import_row_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.source_import_row_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='course_name_snapshot')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.course_name_snapshot';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='department_name')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.department_name';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='credits')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.credits';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='class_composition')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.class_composition';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='major_composition')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.major_composition';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='class_size')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.class_size';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='enrollment_count')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.enrollment_count';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='planned_lab_hours')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.planned_lab_hours';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='weekly_hours')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.weekly_hours';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='original_week_range')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.original_week_range';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='scheduled_week_range')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.scheduled_week_range';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='start_week')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.start_week';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='end_week')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.end_week';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='course_ends_at')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.course_ends_at';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='course_weekly_hours_text')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.course_weekly_hours_text';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='teacher_names_original')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.teacher_names_original';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='locations_original')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.locations_original';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='schedule_original')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.schedule_original';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='created_at')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.created_at';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='updated_at')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.updated_at';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='default_lab_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task.default_lab_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='teaching_task_teacher')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: teaching_task_teacher';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task_teacher' AND column_name='task_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task_teacher.task_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task_teacher' AND column_name='teacher_id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: teaching_task_teacher.teacher_id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='token')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: token';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: token.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token' AND column_name='userid')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: token.userid';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token' AND column_name='username')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: token.username';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token' AND column_name='tablename')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: token.tablename';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token' AND column_name='role')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: token.role';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token' AND column_name='token')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: token.token';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token' AND column_name='addtime')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: token.addtime';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='token' AND column_name='expiratedtime')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: token.expiratedtime';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='users')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Expected exactly one table: users';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='id')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: users.id';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='username')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: users.username';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='password')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: users.password';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='role')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: users.role';
  END IF;
  IF ((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='addtime')) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Missing or conflicting column: users.addtime';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='jiaoshi') = 1 THEN
    RENAME TABLE `jiaoshi` TO `teacher`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='gonghao') = 1 THEN
    ALTER TABLE `teacher` RENAME COLUMN `gonghao` TO `teacher_no`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='mima') = 1 THEN
    ALTER TABLE `teacher` RENAME COLUMN `mima` TO `password`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='jiaoshixingming') = 1 THEN
    ALTER TABLE `teacher` RENAME COLUMN `jiaoshixingming` TO `teacher_name`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='xingbie') = 1 THEN
    ALTER TABLE `teacher` RENAME COLUMN `xingbie` TO `gender`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='touxiang') = 1 THEN
    ALTER TABLE `teacher` RENAME COLUMN `touxiang` TO `avatar_url`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='xueyuan') = 1 THEN
    ALTER TABLE `teacher` RENAME COLUMN `xueyuan` TO `college`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='zhicheng') = 1 THEN
    ALTER TABLE `teacher` RENAME COLUMN `zhicheng` TO `job_title`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teacher' AND column_name='dianhua') = 1 THEN
    ALTER TABLE `teacher` RENAME COLUMN `dianhua` TO `phone`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='teacher' AND index_name='gonghao') > 0 THEN
    ALTER TABLE `teacher` RENAME INDEX `gonghao` TO `teacher_no`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='shiyanshixinxi') = 1 THEN
    RENAME TABLE `shiyanshixinxi` TO `laboratory`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshibianhao') = 1 THEN
    ALTER TABLE `laboratory` RENAME COLUMN `shiyanshibianhao` TO `lab_code`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshimingcheng') = 1 THEN
    ALTER TABLE `laboratory` RENAME COLUMN `shiyanshimingcheng` TO `lab_name`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshiguimo') = 1 THEN
    ALTER TABLE `laboratory` RENAME COLUMN `shiyanshiguimo` TO `lab_size`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshitupian') = 1 THEN
    ALTER TABLE `laboratory` RENAME COLUMN `shiyanshitupian` TO `image_url`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshiweizhi') = 1 THEN
    ALTER TABLE `laboratory` RENAME COLUMN `shiyanshiweizhi` TO `location`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='keyueshijian') = 1 THEN
    ALTER TABLE `laboratory` RENAME COLUMN `keyueshijian` TO `available_hours`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshixiangqing') = 1 THEN
    ALTER TABLE `laboratory` RENAME COLUMN `shiyanshixiangqing` TO `description`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='laboratory' AND column_name='shiyanshizhuangtai') = 1 THEN
    ALTER TABLE `laboratory` RENAME COLUMN `shiyanshizhuangtai` TO `status`;
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='laboratory' AND index_name='shiyanshibianhao') > 0 THEN
    ALTER TABLE `laboratory` RENAME INDEX `shiyanshibianhao` TO `lab_code`;
  END IF;
  ALTER TABLE `academic_term`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `academic_year_id` bigint NOT NULL COMMENT '所属学年ID',
    MODIFY COLUMN `term_no` tinyint NOT NULL COMMENT '学期序号：1第一学期，2第二学期',
    MODIFY COLUMN `starts_on` date DEFAULT NULL COMMENT '学期开始日期',
    MODIFY COLUMN `ends_on` date DEFAULT NULL COMMENT '学期结束日期',
    MODIFY COLUMN `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '学期状态：DRAFT草稿，OPEN开放，ARCHIVED归档',
    COMMENT='学期，状态与实际校历分别管理';
  ALTER TABLE `academic_year`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `name` varchar(9) NOT NULL COMMENT '学年名称，例如2026-2027',
    MODIFY COLUMN `start_year` smallint NOT NULL COMMENT '学年起始年份',
    COMMENT='学年';
  ALTER TABLE `course`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `course_code` varchar(64) NOT NULL COMMENT '课程编号',
    MODIFY COLUMN `course_name` varchar(200) NOT NULL COMMENT '课程名称',
    MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    COMMENT='跨学期课程基础信息';
  ALTER TABLE `experiment_project`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '所属教学任务ID',
    MODIFY COLUMN `project_code` varchar(64) NOT NULL COMMENT '实验项目编号',
    MODIFY COLUMN `school_code` varchar(32) NOT NULL COMMENT '学校代码',
    MODIFY COLUMN `name` varchar(50) NOT NULL COMMENT '实验项目名称',
    MODIFY COLUMN `category_code` char(1) NOT NULL COMMENT '实验类别代码：1基础，2专业基础，3专业，4其他',
    MODIFY COLUMN `type_code` varchar(2) NOT NULL COMMENT '实验类型代码：1演示，2验证，3综合，4设计研究，5其他',
    MODIFY COLUMN `discipline_code` varchar(16) NOT NULL COMMENT '所属学科代码',
    MODIFY COLUMN `requirement_code` char(1) NOT NULL COMMENT '实验要求代码：1必修，2选修，3其他',
    MODIFY COLUMN `participant_type_code` char(1) NOT NULL COMMENT '实验者类别代码：1博士生，2硕士生，3本科生，4专科生，5其他',
    MODIFY COLUMN `group_size` smallint NOT NULL COMMENT '每组实验人数',
    MODIFY COLUMN `hours` decimal(6,2) NOT NULL COMMENT '实验学时',
    MODIFY COLUMN `sort_order` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
    MODIFY COLUMN `copied_from_id` bigint DEFAULT NULL COMMENT '复制来源实验项目ID',
    MODIFY COLUMN `created_by_teacher_id` bigint DEFAULT NULL COMMENT '创建人教师ID，管理员创建时为空',
    MODIFY COLUMN `updated_by_teacher_id` bigint DEFAULT NULL COMMENT '最后修改人教师ID，管理员修改时为空',
    MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    COMMENT='教学任务的实验项目版本，不自动导入模板示例';
  ALTER TABLE `teacher`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `teacher_no` varchar(200) NOT NULL COMMENT '工号',
    MODIFY COLUMN `password` varchar(200) NOT NULL COMMENT '登录密码摘要',
    MODIFY COLUMN `teacher_name` varchar(200) DEFAULT NULL COMMENT '教师姓名',
    MODIFY COLUMN `gender` varchar(200) DEFAULT NULL COMMENT '性别',
    MODIFY COLUMN `avatar_url` varchar(200) DEFAULT NULL COMMENT '头像',
    MODIFY COLUMN `college` varchar(200) DEFAULT NULL COMMENT '学院',
    MODIFY COLUMN `job_title` varchar(200) DEFAULT NULL COMMENT '职称',
    MODIFY COLUMN `phone` varchar(200) DEFAULT NULL COMMENT '电话',
    COMMENT='教师';
  ALTER TABLE `schedule_detail`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '所属教学任务ID',
    MODIFY COLUMN `lab_id` bigint NOT NULL COMMENT '实验室ID',
    MODIFY COLUMN `teaching_week` smallint NOT NULL COMMENT '教学周次',
    MODIFY COLUMN `weekday` tinyint NOT NULL COMMENT '星期：1周一至7周日',
    MODIFY COLUMN `period_start` smallint NOT NULL COMMENT '开始节次',
    MODIFY COLUMN `period_end` smallint NOT NULL COMMENT '结束节次',
    MODIFY COLUMN `hours` decimal(6,2) NOT NULL COMMENT '实验学时',
    MODIFY COLUMN `source_segment` int NOT NULL COMMENT '原始排课片段序号',
    COMMENT='按教学周和连续节次拆分的实验室排课';
  ALTER TABLE `laboratory`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `lab_code` varchar(200) NOT NULL COMMENT '实验室编号',
    MODIFY COLUMN `lab_name` varchar(200) NOT NULL COMMENT '实验室名称',
    MODIFY COLUMN `lab_size` varchar(200) NOT NULL COMMENT '实验室规模',
    MODIFY COLUMN `image_url` varchar(200) DEFAULT NULL COMMENT '实验室图片',
    MODIFY COLUMN `location` varchar(200) DEFAULT NULL COMMENT '实验室位置',
    MODIFY COLUMN `available_hours` varchar(200) DEFAULT NULL COMMENT '可约时间',
    MODIFY COLUMN `description` longtext COMMENT '实验室详情',
    MODIFY COLUMN `status` varchar(200) NOT NULL COMMENT '实验室状态',
    MODIFY COLUMN `manager_teacher_id` bigint DEFAULT NULL COMMENT '负责人教师ID',
    MODIFY COLUMN `equipment_count` int DEFAULT NULL COMMENT '设备数，NULL代表未知',
    COMMENT='实验室信息';
  ALTER TABLE `teaching_import_batch`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `file_sha256` char(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '导入文件SHA256摘要，用于重复导入校验',
    MODIFY COLUMN `file_name` varchar(255) NOT NULL COMMENT '导入文件名',
    MODIFY COLUMN `parser_version` varchar(32) NOT NULL COMMENT '课表解析器版本',
    MODIFY COLUMN `row_count` int NOT NULL COMMENT '导入数据行数',
    MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    COMMENT='课表导入批次，同一文件内容只暂存一次';
  ALTER TABLE `teaching_import_row`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `batch_id` bigint NOT NULL COMMENT '所属导入批次ID',
    MODIFY COLUMN `sheet_name` varchar(64) NOT NULL COMMENT '来源工作表名称',
    MODIFY COLUMN `source_row` int NOT NULL COMMENT '来源Excel行号',
    MODIFY COLUMN `raw_data` json NOT NULL COMMENT '原始行数据JSON',
    MODIFY COLUMN `parsed_schedule` json DEFAULT NULL COMMENT '解析后的排课数据JSON',
    MODIFY COLUMN `issues` json NOT NULL COMMENT '解析问题与待核对事项JSON',
    MODIFY COLUMN `status` varchar(16) NOT NULL DEFAULT 'REVIEW' COMMENT '导入行状态：REVIEW待核对，ERROR错误，PROMOTED已转教学任务',
    COMMENT='原始课表暂存，未确认身份和口径前不转正式任务';
  ALTER TABLE `teaching_task`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `task_code` varchar(64) NOT NULL COMMENT '教学任务编号',
    MODIFY COLUMN `term_id` bigint NOT NULL COMMENT '所属学期ID',
    MODIFY COLUMN `course_id` bigint NOT NULL COMMENT '课程ID',
    MODIFY COLUMN `source_import_row_id` bigint DEFAULT NULL COMMENT '来源导入行ID，手工创建时为空',
    MODIFY COLUMN `course_name_snapshot` varchar(200) NOT NULL COMMENT '开课时的课程名称快照',
    MODIFY COLUMN `department_name` varchar(200) NOT NULL COMMENT '开课学院名称',
    MODIFY COLUMN `credits` decimal(6,2) NOT NULL COMMENT '课程学分',
    MODIFY COLUMN `class_composition` text NOT NULL COMMENT '授课班级组成',
    MODIFY COLUMN `major_composition` text COMMENT '授课专业组成',
    MODIFY COLUMN `class_size` int NOT NULL COMMENT '班级人数',
    MODIFY COLUMN `enrollment_count` int NOT NULL COMMENT '选课人数',
    MODIFY COLUMN `planned_lab_hours` decimal(8,2) NOT NULL COMMENT '计划实验总学时',
    MODIFY COLUMN `weekly_hours` decimal(6,2) NOT NULL COMMENT '每周实验学时',
    MODIFY COLUMN `original_week_range` varchar(255) NOT NULL COMMENT '原始课表周次范围',
    MODIFY COLUMN `scheduled_week_range` text NOT NULL COMMENT '实际排课周次范围',
    MODIFY COLUMN `start_week` smallint NOT NULL COMMENT '开始教学周',
    MODIFY COLUMN `end_week` smallint NOT NULL COMMENT '结束教学周',
    MODIFY COLUMN `course_ends_at` datetime DEFAULT NULL COMMENT '课程结束时间',
    MODIFY COLUMN `course_weekly_hours_text` varchar(255) NOT NULL COMMENT '原始课程周学时文本',
    MODIFY COLUMN `teacher_names_original` text NOT NULL COMMENT '原始授课教师姓名文本',
    MODIFY COLUMN `locations_original` text NOT NULL COMMENT '原始上课地点文本',
    MODIFY COLUMN `schedule_original` text NOT NULL COMMENT '原始排课时间文本',
    MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    MODIFY COLUMN `default_lab_id` bigint DEFAULT NULL COMMENT '默认实验室ID，用于尚未排课的任务',
    COMMENT='学期开课任务，不以班级名称去重';
  ALTER TABLE `teaching_task_teacher`
    MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '所属教学任务ID',
    MODIFY COLUMN `teacher_id` bigint NOT NULL COMMENT '授课教师ID',
    COMMENT='合授教师关联，姓名不是身份主键';
  ALTER TABLE `token`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `userid` bigint NOT NULL COMMENT '用户id',
    MODIFY COLUMN `username` varchar(100) NOT NULL COMMENT '用户名',
    MODIFY COLUMN `tablename` varchar(100) DEFAULT NULL COMMENT '表名',
    MODIFY COLUMN `role` varchar(100) DEFAULT NULL COMMENT '角色',
    MODIFY COLUMN `token` varchar(200) NOT NULL COMMENT '登录令牌',
    MODIFY COLUMN `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '新增时间',
    MODIFY COLUMN `expiratedtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '过期时间',
    COMMENT='登录令牌表';
  ALTER TABLE `users`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    MODIFY COLUMN `username` varchar(100) NOT NULL COMMENT '用户名',
    MODIFY COLUMN `password` varchar(100) NOT NULL COMMENT '登录密码摘要',
    MODIFY COLUMN `role` varchar(100) DEFAULT '管理员' COMMENT '角色',
    MODIFY COLUMN `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '新增时间',
    COMMENT='用户表';
  UPDATE token SET tablename='teacher' WHERE tablename='jiaoshi';
  UPDATE token SET tablename='laboratory' WHERE tablename='shiyanshixinxi';
END$$
CALL migrate_english_schema_004()$$
DROP PROCEDURE migrate_english_schema_004$$
DELIMITER ;
