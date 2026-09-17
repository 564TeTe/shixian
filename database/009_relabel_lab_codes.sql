-- ============================================================
-- 实验室编号改为 10 位数字：前 7 位固定 2203200，后 3 位递增
-- 样例：2203200102（第 1 个）→ 2203200103 → ... → 2203200115
-- 用 UPDATE 保留 id，不影响 schedule_detail 外键
-- ============================================================

USE t132;
SET NAMES utf8mb4;

-- 1. 备份旧 lab_code（写入临时表，方便回滚）
DROP TABLE IF EXISTS lab_code_backup_20260917;
CREATE TABLE lab_code_backup_20260917 AS
SELECT id, lab_code AS old_lab_code, lab_name, lab_code AS new_lab_code_placeholder
FROM laboratory;

-- 2. 按 id 升序生成新编号
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM laboratory
)
UPDATE laboratory l
JOIN ranked r ON l.id = r.id
SET l.lab_code = CONCAT('2203200', LPAD(101 + r.rn, 3, '0'));

-- 3. 核对结果
SELECT b.id, b.old_lab_code, l.lab_code AS new_lab_code
FROM lab_code_backup_20260917 b
JOIN laboratory l ON l.id = b.id
ORDER BY l.id;
