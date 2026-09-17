-- AI教师授课统计只读视图
-- 仅向AI只读账号暴露教师姓名、教学任务、课程和班级组成，不暴露账号或密码字段。

USE `t132`;

CREATE OR REPLACE SQL SECURITY DEFINER VIEW `ai_teacher_workload` AS
SELECT
    `t`.`id` AS `task_id`,
    `a`.`display_name` AS `teacher_name`,
    `t`.`course_name_snapshot` AS `course_name`,
    `t`.`class_composition` AS `class_composition`
FROM `teaching_task` `t`
JOIN `teaching_task_teacher` `l` ON `l`.`task_id` = `t`.`id`
JOIN `account` `a` ON `a`.`id` = `l`.`teacher_account_id`
WHERE `a`.`role` = 'TEACHER';

GRANT SELECT ON `t132`.`ai_teacher_workload` TO 'ai12345'@'127.0.0.1';
FLUSH PRIVILEGES;
