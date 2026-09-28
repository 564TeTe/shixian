-- Aiven MySQL 8.0.18+ / 8.4：智能查询的云端只读账号和授课视图。
-- 先在新演示库完成 T132.sql 和 003_teaching_seed.sql，再以 avnadmin 执行。
-- 本文件替代云端的原版 005、006、007，不修改业务数据。
-- 首次 CREATE USER 会返回 Generated password，请私下保存到 Render 环境变量。
-- 重跑不会重置已有密码；仅用于本项目专用的 teaching_ai_reader 账号。
-- 重跑不清理已有授权；必须核对最后的 SHOW GRANTS 只有 USAGE 和 t132 的 SELECT。

USE `t132`;

CREATE OR REPLACE SQL SECURITY INVOKER VIEW `ai_teacher_workload` AS
SELECT
    t.id AS task_id,
    a.display_name AS teacher_name,
    t.course_name_snapshot AS course_name,
    t.class_composition AS class_composition
FROM teaching_task t
JOIN teaching_task_teacher l ON l.task_id = t.id
JOIN account a ON a.id = l.teacher_account_id
WHERE a.role = 'TEACHER';

CREATE USER IF NOT EXISTS 'teaching_ai_reader'@'%'
    IDENTIFIED BY RANDOM PASSWORD REQUIRE SSL;

-- 保留密码并要求加密连接，只对业务库授予 SELECT。
-- Aiven 限制系统库权限，不能执行全局 REVOKE ALL PRIVILEGES, GRANT OPTION。
-- 本脚本不接管已有管理员账号；若发现额外权限或角色，先停止部署并单独处理。
ALTER USER 'teaching_ai_reader'@'%' REQUIRE SSL;
GRANT SELECT ON `t132`.* TO 'teaching_ai_reader'@'%';

-- GRANT 自动生效，不需要 FLUSH PRIVILEGES。
SHOW GRANTS FOR 'teaching_ai_reader'@'%';
SELECT COUNT(*) AS teacher_workload_rows FROM `ai_teacher_workload`;
