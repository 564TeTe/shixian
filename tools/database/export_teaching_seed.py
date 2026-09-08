"""Export a shareable teaching snapshot without copying local credentials.

Read-only source access. Requires MYSQL_PWD and bootstrap-requirements.txt.
The generated SQL is for an empty schema, never for updating an existing site.
"""
import argparse
from datetime import date, datetime
from decimal import Decimal
import json
import os
from pathlib import Path
import re
import secrets

import bcrypt
import pymysql


ROOT = Path(__file__).resolve().parents[2]
INITIAL_ADMIN_PASSWORD = 'Teaching2026!'
# Explicit fields for people/resources: omit contact details, pictures and local passwords.
FIELDS = {
    'academic_year': 'id,name,start_year',
    'academic_term': 'id,academic_year_id,term_no,starts_on,ends_on,status',
    'jiaoshi': 'id,gonghao,jiaoshixingming,xueyuan',
    'shiyanshixinxi': 'id,shiyanshibianhao,shiyanshimingcheng,shiyanshiguimo,shiyanshiweizhi,shiyanshizhuangtai,manager_teacher_id,equipment_count',
    'course': 'id,course_code,course_name',
    'teaching_import_batch': 'id,file_sha256,file_name,parser_version,row_count',
    'teaching_import_row': 'id,batch_id,sheet_name,source_row,raw_data,parsed_schedule,issues,status',
    'teaching_task': 'id,task_code,term_id,course_id,source_import_row_id,course_name_snapshot,department_name,credits,class_composition,major_composition,class_size,enrollment_count,planned_lab_hours,weekly_hours,original_week_range,scheduled_week_range,start_week,end_week,course_ends_at,course_weekly_hours_text,teacher_names_original,locations_original,schedule_original,default_lab_id',
    'teaching_task_teacher': 'task_id,teacher_id',
    'schedule_detail': 'id,task_id,lab_id,teaching_week,weekday,period_start,period_end,hours,source_segment',
    'experiment_project': 'id,task_id,project_code,school_code,name,category_code,type_code,discipline_code,requirement_code,participant_type_code,group_size,hours,sort_order,copied_from_id,created_by_teacher_id,updated_by_teacher_id',
}
LEGACY = ['caigoujilu', 'config', 'discussgonggaoxinxi', 'discussshiyankecheng',
          'gonggaoxinxi', 'shiyankecheng', 'shiyanshebei', 'shiyanshiyuyue',
          'storeup', 'token', 'weixiujilu', 'xuesheng', 'zhishiku']


def literal(value):
    """SQL is created under NO_BACKSLASH_ESCAPES; quote apostrophes only."""
    if value is None:
        return 'NULL'
    if isinstance(value, (int, Decimal)):
        return str(value)
    if isinstance(value, (date, datetime)):
        value = value.isoformat(sep=' ') if isinstance(value, datetime) else value.isoformat()
    if not isinstance(value, str):
        raise TypeError('Unsupported seed value type: ' + type(value).__name__)
    return "'" + value.replace("'", "''") + "'"


def export(connection):
    snapshot = {}
    with connection.cursor(pymysql.cursors.DictCursor) as cursor:
        cursor.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
        for table, fields in FIELDS.items():
            order = 'task_id,teacher_id' if table == 'teaching_task_teacher' else 'id'
            cursor.execute(f'SELECT {fields} FROM `{table}` ORDER BY {order}')
            snapshot[table] = list(cursor.fetchall())
    connection.rollback()
    # The shared dataset follows the timetable. Keep later local-only, unused rooms local.
    lab_ids = {row['lab_id'] for row in snapshot['schedule_detail']}
    lab_ids.update(row['default_lab_id'] for row in snapshot['teaching_task'] if row['default_lab_id'] is not None)
    snapshot['shiyanshixinxi'] = [row for row in snapshot['shiyanshixinxi'] if row['id'] in lab_ids]
    for teacher in snapshot['jiaoshi']:
        # Unknown random passwords; the new site's administrator resets each teacher password.
        teacher['mima'] = bcrypt.hashpw(secrets.token_urlsafe(32).encode(), bcrypt.gensalt(10)).decode()
    for batch in snapshot['teaching_import_batch']:
        batch['file_name'] = batch['file_name'].replace('\\', '/').rsplit('/', 1)[-1]
    if any(row['copied_from_id'] is not None for row in snapshot['experiment_project']):
        raise ValueError('Export of copied projects needs a two-pass relationship migration')
    snapshot['users'] = [dict(id=1, username='admin', role='管理员', password=bcrypt.hashpw(
        INITIAL_ADMIN_PASSWORD.encode(), bcrypt.gensalt(10)).decode())]
    counts = {table: len(rows) for table, rows in snapshot.items()}
    guard = ' + '.join(f'(SELECT COUNT(*) FROM `{table}`)' for table in [*FIELDS, 'users', *LEGACY])
    sql = [
        '-- Shared teaching data: import T132.sql into an EMPTY database first.',
        '-- Initial administrator: admin / Teaching2026! (public development login; change after login).',
        '-- Teacher passwords are new random hashes; use administrator password reset.',
        '-- No source passwords, tokens, AI keys or MySQL users are exported.',
        '-- Existing records cause an error; all inserts run in one rollback-protected transaction.',
        '-- Counts: ' + json.dumps(counts, ensure_ascii=False),
        'SET NAMES utf8mb4;',
        'SET @teaching_seed_old_mode = @@SESSION.sql_mode;',
        "SET SESSION sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION,NO_BACKSLASH_ESCAPES';",
        'DELIMITER $$',
        'CREATE PROCEDURE install_teaching_seed_20260908()',
        'BEGIN',
        '  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;',
        '  START TRANSACTION;',
        f'  IF ({guard}) <> 0 THEN',
        "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Teaching seed requires empty tables; existing data was not changed';",
        '  END IF;',
    ]
    for table, rows in snapshot.items():
        if not rows:
            continue
        fields = list(rows[0])
        for offset in range(0, len(rows), 50):
            values = [ '(' + ','.join(literal(row[key]) for key in fields) + ')' for row in rows[offset:offset+50] ]
            sql.append('  INSERT INTO `' + table + '` (' + ','.join('`'+key+'`' for key in fields) + ') VALUES\n    ' + ',\n    '.join(values) + ';')
    sql.extend(['  COMMIT;', 'END$$', 'DELIMITER ;', 'CALL install_teaching_seed_20260908();',
                'DROP PROCEDURE install_teaching_seed_20260908;',
                'SET SESSION sql_mode = @teaching_seed_old_mode;', ''])
    return '\n'.join(sql), counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', default='t132')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--output', type=Path, default=ROOT/'database/003_teaching_seed.sql')
    args = parser.parse_args()
    if not re.fullmatch('[A-Za-z0-9_]+', args.database) or 'MYSQL_PWD' not in os.environ:
        parser.error('Provide a valid database and MYSQL_PWD environment variable')
    connection = pymysql.connect(host=args.host, port=args.port, user=args.user,
        password=os.environ['MYSQL_PWD'], database=args.database, charset='utf8mb4')
    try:
        sql, counts = export(connection)
    finally:
        connection.close()
    args.output.write_text(sql, encoding='utf-8')
    print(json.dumps({'output':str(args.output),'counts':counts}, ensure_ascii=False))


if __name__ == '__main__':
    main()
