-- ============================================================
-- 为 2026-2027 学年第 1 学期（term_id=1）生成 5 条教学任务
-- 挂真实课程（course）+ 真实教师（account）
-- 任务编号 IMP-9-264 ~ IMP-9-268
-- 本脚本幂等：已存在的编号自动跳过，可重复执行
-- ============================================================

USE t132;
SET NAMES utf8mb4;

-- 1) 教学任务：已存在对应 task_code 则跳过
INSERT INTO teaching_task
(task_code, term_id, course_id, course_name_snapshot, class_composition, major_composition,
 enrollment_count, planned_lab_hours)
SELECT 'IMP-9-264', 1, 25,  '离散数学',         '26数据科学与大数据技术1班', '数据科学与大数据技术', 42, 16.00 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM teaching_task WHERE task_code = 'IMP-9-264');

INSERT INTO teaching_task
(task_code, term_id, course_id, course_name_snapshot, class_composition, major_composition,
 enrollment_count, planned_lab_hours)
SELECT 'IMP-9-265', 1, 81,  '数据结构与算法',   '26软件工程1班+2班',         '软件工程',             86, 24.00 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM teaching_task WHERE task_code = 'IMP-9-265');

INSERT INTO teaching_task
(task_code, term_id, course_id, course_name_snapshot, class_composition, major_composition,
 enrollment_count, planned_lab_hours)
SELECT 'IMP-9-266', 1, 54,  'Python程序设计',   '26计算机科学与技术1班',     '计算机科学与技术',     45, 20.00 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM teaching_task WHERE task_code = 'IMP-9-266');

INSERT INTO teaching_task
(task_code, term_id, course_id, course_name_snapshot, class_composition, major_composition,
 enrollment_count, planned_lab_hours)
SELECT 'IMP-9-267', 1, 205, '数据库原理与应用', '26数据科学与大数据技术2班', '数据科学与大数据技术', 48, 20.00 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM teaching_task WHERE task_code = 'IMP-9-267');

INSERT INTO teaching_task
(task_code, term_id, course_id, course_name_snapshot, class_composition, major_composition,
 enrollment_count, planned_lab_hours)
SELECT 'IMP-9-268', 1, 199, '计算机网络基础',   '26网络工程1班',             '网络工程',             40, 18.00 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM teaching_task WHERE task_code = 'IMP-9-268');

-- 2) 教师关联：已存在的 (task_id, teacher_account_id) 自动跳过
INSERT INTO teaching_task_teacher (task_id, teacher_account_id)
SELECT t.id, 87 FROM teaching_task t
WHERE t.task_code = 'IMP-9-264'
  AND NOT EXISTS (SELECT 1 FROM teaching_task_teacher tt WHERE tt.task_id = t.id AND tt.teacher_account_id = 87);

INSERT INTO teaching_task_teacher (task_id, teacher_account_id)
SELECT t.id, 88 FROM teaching_task t
WHERE t.task_code = 'IMP-9-265'
  AND NOT EXISTS (SELECT 1 FROM teaching_task_teacher tt WHERE tt.task_id = t.id AND tt.teacher_account_id = 88);

INSERT INTO teaching_task_teacher (task_id, teacher_account_id)
SELECT t.id, 89 FROM teaching_task t
WHERE t.task_code = 'IMP-9-265'
  AND NOT EXISTS (SELECT 1 FROM teaching_task_teacher tt WHERE tt.task_id = t.id AND tt.teacher_account_id = 89);

INSERT INTO teaching_task_teacher (task_id, teacher_account_id)
SELECT t.id, 90 FROM teaching_task t
WHERE t.task_code = 'IMP-9-266'
  AND NOT EXISTS (SELECT 1 FROM teaching_task_teacher tt WHERE tt.task_id = t.id AND tt.teacher_account_id = 90);

INSERT INTO teaching_task_teacher (task_id, teacher_account_id)
SELECT t.id, 91 FROM teaching_task t
WHERE t.task_code = 'IMP-9-267'
  AND NOT EXISTS (SELECT 1 FROM teaching_task_teacher tt WHERE tt.task_id = t.id AND tt.teacher_account_id = 91);

INSERT INTO teaching_task_teacher (task_id, teacher_account_id)
SELECT t.id, 92 FROM teaching_task t
WHERE t.task_code = 'IMP-9-268'
  AND NOT EXISTS (SELECT 1 FROM teaching_task_teacher tt WHERE tt.task_id = t.id AND tt.teacher_account_id = 92);

-- 3) 核对（重复执行也不应报错、不应产生新行）
SELECT t.id, t.task_code, c.course_name, t.enrollment_count, t.planned_lab_hours,
       GROUP_CONCAT(a.display_name ORDER BY a.id) AS teachers
FROM teaching_task t
LEFT JOIN course c ON c.id = t.course_id
LEFT JOIN teaching_task_teacher tt ON tt.task_id = t.id
LEFT JOIN account a ON a.id = tt.teacher_account_id
WHERE t.task_code IN ('IMP-9-264','IMP-9-265','IMP-9-266','IMP-9-267','IMP-9-268')
GROUP BY t.id, t.task_code, c.course_name, t.enrollment_count, t.planned_lab_hours
ORDER BY t.task_code;
