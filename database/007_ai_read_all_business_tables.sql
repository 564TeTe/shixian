-- 允许AI只读账号读取t132数据库中的全部表和视图。
-- 应用层仍会拒绝密码、令牌和密钥字段。

GRANT SELECT ON `t132`.* TO 'ai12345'@'127.0.0.1';
FLUSH PRIVILEGES;
