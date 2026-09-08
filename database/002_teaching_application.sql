-- Run after 001. Optional initial laboratory for a task before its timetable exists.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_task' AND column_name='default_lab_id')=0,
 'ALTER TABLE teaching_task ADD COLUMN default_lab_id BIGINT NULL', 'SELECT 1');
PREPARE app_migration FROM @ddl;
EXECUTE app_migration;
DEALLOCATE PREPARE app_migration;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_task_default_lab')=0,
 'ALTER TABLE teaching_task ADD CONSTRAINT fk_task_default_lab FOREIGN KEY (default_lab_id) REFERENCES shiyanshixinxi(id)', 'SELECT 1');
PREPARE app_migration FROM @ddl;
EXECUTE app_migration;
DEALLOCATE PREPARE app_migration;
