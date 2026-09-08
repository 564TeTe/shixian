"""Run integration checks in a newly created, isolated MySQL database.

Credentials come from MYSQL_PWD (never command-line passwords). The source
database is only read for the two legacy table definitions. Own scratch database
is dropped on exit; no source data is copied or modified.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import uuid

from schedule_import import analyze, staging_sql


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-database', default='t132')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--mysql', default=shutil.which('mysql'))
    parser.add_argument('--workbook', required=True, type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    if not args.mysql or not re.fullmatch(r'[A-Za-z0-9_]+', args.source_database):
        parser.error('需要 mysql 客户端和合法的源数据库名称')
    root = Path(__file__).resolve().parents[2]
    scratch = 'codex_lab_verify_' + uuid.uuid4().hex[:12]
    command = [args.mysql, '--host=' + args.host, '--port=' + str(args.port),
               '--user=' + args.user, '--batch', '--skip-column-names', '--raw',
               '--default-character-set=utf8mb4']

    def execute(sql, database=None, expected_error=None):
        result = subprocess.run(command + (['--database=' + database] if database else []),
                                input=sql, encoding='utf-8', capture_output=True)
        if expected_error:
            if result.returncode == 0 or expected_error not in result.stderr:
                raise AssertionError(f'未得到预期错误 {expected_error}: {result.stderr}')
        elif result.returncode:
            raise RuntimeError(result.stderr)
        return result.stdout.strip()

    checks = []
    execute(f'CREATE DATABASE `{scratch}` CHARACTER SET utf8mb4;')
    try:
        for table in ['jiaoshi', 'shiyanshixinxi']:
            execute(f'CREATE TABLE `{table}` LIKE `{args.source_database}`.`{table}`;', scratch)
        ddl = (root / 'database/001_teaching_foundation.sql').read_text(encoding='utf-8')
        execute(ddl, scratch)
        execute(ddl, scratch)
        tables = execute('SHOW TABLES;', scratch).splitlines()
        assert len(tables) == 11, tables
        checks.append('DDL首次执行及重复执行成功，11张表存在')
        execute("ALTER TABLE shiyanshixinxi DROP FOREIGN KEY fk_lab_manager;", scratch)
        execute(ddl, scratch)
        assert execute("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND constraint_name='fk_lab_manager';", scratch) == '1'
        checks.append('字段已存在但约束缺失时可修复')

        execute("INSERT INTO academic_year (name,start_year) VALUES ('2025-2026',2025);"
                "INSERT INTO academic_term (academic_year_id,term_no,status) VALUES (1,1,'ARCHIVED');"
                "INSERT INTO course (course_code,course_name) VALUES ('00123','验证课程');"
                "INSERT INTO jiaoshi (id,gonghao,mima) VALUES (90001,'test-90001','disabled-test-account');"
                "INSERT INTO shiyanshixinxi (id,shiyanshibianhao,shiyanshimingcheng,shiyanshiguimo,shiyanshizhuangtai) VALUES (90001,'TEST-ROOM','验证实验室','测试','测试');", scratch)
        execute("INSERT INTO academic_year (name,start_year) VALUES ('2020-2025',2020);", scratch, '3819')
        execute("INSERT INTO academic_term (academic_year_id,term_no) VALUES (1,3);", scratch, '3819')
        execute("INSERT INTO teaching_task_teacher (task_id,teacher_id) VALUES (999,90001);", scratch, '1452')
        execute("UPDATE shiyanshixinxi SET equipment_count=-1 WHERE id=90001;", scratch, '3819')
        execute("UPDATE shiyanshixinxi SET manager_teacher_id=999999 WHERE id=90001;", scratch, '1452')
        checks.append('学年/学期/设备数非法值及悬空外键均被拒绝')

        task = """INSERT INTO teaching_task (id,task_code,term_id,course_id,course_name_snapshot,department_name,
          credits,class_composition,class_size,enrollment_count,planned_lab_hours,weekly_hours,
          original_week_range,scheduled_week_range,start_week,end_week,course_weekly_hours_text,
          teacher_names_original,locations_original,schedule_original)
          VALUES (90001,'TEST-TASK',1,1,'验证课程','测试',2,'测试班',50,47,12,4,'6-8周','6-8周',6,8,
          '实验(4)','教师','TEST-ROOM','星期二第5-8节{6-8周}');"""
        execute(task, scratch)
        execute("INSERT INTO teaching_task_teacher VALUES (90001,90001);", scratch)
        execute("INSERT INTO teaching_task_teacher VALUES (90001,90001);", scratch, '1062')
        execute("INSERT INTO schedule_detail (task_id,lab_id,teaching_week,weekday,period_start,period_end,hours,source_segment) VALUES (90001,90001,6,2,5,8,4,1),(90001,90001,7,2,5,8,4,1);", scratch)
        assert execute("SELECT SUM(s.hours)*MAX(t.enrollment_count) FROM schedule_detail s JOIN teaching_task t ON t.id=s.task_id;", scratch) == '376.00'
        execute("INSERT INTO experiment_project (task_id,project_code,school_code,name,category_code,type_code,discipline_code,requirement_code,participant_type_code,group_size,hours) VALUES (90001,'TEST-P','11059','测试项目','3','2','0809','1','3',1,2);", scratch)
        assert execute("SELECT discipline_code FROM experiment_project WHERE project_code='TEST-P';", scratch) == '0809'
        execute("UPDATE experiment_project SET group_size=0 WHERE project_code='TEST-P';", scratch, '3819')
        checks.append('合授关联防重复、人时数376、学科前导零和项目范围校验通过')

        records, report = analyze(args.workbook)
        sql = staging_sql(records, report)
        execute(sql, scratch)
        first = execute('SELECT COUNT(*) FROM teaching_import_batch; SELECT COUNT(*) FROM teaching_import_row;', scratch)
        assert first == f'1\n{len(records)}', first
        execute("UPDATE teaching_import_row SET status='PROMOTED' WHERE source_row=2;", scratch)
        execute(sql, scratch)
        assert execute('SELECT COUNT(*) FROM teaching_import_batch; SELECT COUNT(*) FROM teaching_import_row;', scratch) == first
        assert execute("SELECT status FROM teaching_import_row WHERE source_row=2;", scratch) == 'PROMOTED'
        decoded = execute("SELECT JSON_UNQUOTE(JSON_EXTRACT(raw_data,'$.\"课程名称\"')) FROM teaching_import_row WHERE source_row=2;", scratch)
        assert decoded == records[0]['raw']['课程名称'], decoded
        checks.append(f'{len(records)}行暂存成功、中文JSON无损、重复导入不增生也不覆盖审核状态')
        summary = dict(mysql_version=execute('SELECT VERSION();', scratch), checks=checks,
                       staging_rows=len(records), expanded_schedule_rows=report['expanded_schedule_rows'],
                       outcome='PASS')
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        print(json.dumps(summary, ensure_ascii=False, indent=2))
    finally:
        # scratch was generated locally and CREATE (without IF NOT EXISTS) succeeded.
        execute(f'DROP DATABASE `{scratch}`;')


if __name__ == '__main__':
    main()
