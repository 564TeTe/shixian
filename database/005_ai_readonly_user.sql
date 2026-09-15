-- AI只读数据库账号初始化脚本
-- MySQL 8.0+
-- 用法：
-- 1. 使用root或其他具有CREATE USER、GRANT权限的管理员账号执行本文件。
-- 2. 只修改下面两个变量，不要把真实密码提交到Git。
-- 3. 当前AI Demo只允许读取course和teaching_task。
--
-- 推荐账号名：teaching_ai_reader
-- 账号连接来源：127.0.0.1

USE `t132`;

SET @ai_reader_username = 'ai12345';
SET @ai_reader_password = 'aiuser';

DELIMITER $$

DROP PROCEDURE IF EXISTS `install_ai_readonly_user`$$

CREATE PROCEDURE `install_ai_readonly_user`()
BEGIN
    IF @ai_reader_username IS NULL
       OR @ai_reader_username = '请填写AI只读账号名'
       OR @ai_reader_username NOT REGEXP '^[A-Za-z0-9_]+$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'AI只读账号名只能包含英文字母、数字和下划线';
    END IF;

    IF @ai_reader_password IS NULL
       OR @ai_reader_password = ''
       OR @ai_reader_password = '请填写AI只读账号密码' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '请先修改005脚本中的@ai_reader_password';
    END IF;

    SET @create_user_sql = CONCAT(
        'CREATE USER IF NOT EXISTS ',
        QUOTE(@ai_reader_username),
        '@',
        QUOTE('127.0.0.1'),
        ' IDENTIFIED BY ',
        QUOTE(@ai_reader_password)
    );
    PREPARE create_user_statement FROM @create_user_sql;
    EXECUTE create_user_statement;
    DEALLOCATE PREPARE create_user_statement;

    SET @alter_user_sql = CONCAT(
        'ALTER USER ',
        QUOTE(@ai_reader_username),
        '@',
        QUOTE('127.0.0.1'),
        ' IDENTIFIED BY ',
        QUOTE(@ai_reader_password)
    );
    PREPARE alter_user_statement FROM @alter_user_sql;
    EXECUTE alter_user_statement;
    DEALLOCATE PREPARE alter_user_statement;

    SET @grant_course_sql = CONCAT(
        'GRANT SELECT ON `t132`.`course` TO ',
        QUOTE(@ai_reader_username),
        '@',
        QUOTE('127.0.0.1')
    );
    PREPARE grant_course_statement FROM @grant_course_sql;
    EXECUTE grant_course_statement;
    DEALLOCATE PREPARE grant_course_statement;

    SET @grant_task_sql = CONCAT(
        'GRANT SELECT ON `t132`.`teaching_task` TO ',
        QUOTE(@ai_reader_username),
        '@',
        QUOTE('127.0.0.1')
    );
    PREPARE grant_task_statement FROM @grant_task_sql;
    EXECUTE grant_task_statement;
    DEALLOCATE PREPARE grant_task_statement;

    FLUSH PRIVILEGES;
END$$

CALL `install_ai_readonly_user`()$$

DROP PROCEDURE `install_ai_readonly_user`$$

DELIMITER ;

-- 权限确认示例：
-- SHOW GRANTS FOR 'teaching_ai_reader'@'127.0.0.1';
