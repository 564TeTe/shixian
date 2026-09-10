"""Replace legacy demo data with the supplied teaching timetable (explicit reset only).

MYSQL_PWD is required. A full mysqldump backup is written before mutation.
Credentials and backups go into database/generated, which is excluded from Git.
"""
import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys

import bcrypt
import pymysql
from schedule_import import analyze, PARSER_VERSION


LEGACY = []


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workbook', required=True, type=Path)
    parser.add_argument('--reset-legacy-data', action='store_true', required=True)
    parser.add_argument('--database', default='t132')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_]+', args.database) or 'MYSQL_PWD' not in os.environ:
        parser.error('请提供合法数据库名并在环境中设置MYSQL_PWD')
    records, report = analyze(args.workbook)
    if any(any(i.startswith('INVALID:') for i in r['issues']) for r in records):
        parser.error('课表仍有无法解析的数据，未清理数据库')
    root = Path(__file__).resolve().parents[2]
    output = root / 'database/generated'
    output.mkdir(parents=True, exist_ok=True)
    tag = datetime.now().strftime('%Y%m%d-%H%M%S') + '-' + secrets.token_hex(3)
    backup = output / ('before-demo-cleanup-' + tag + '.sql')
    dumper = shutil.which('mysqldump')
    if not dumper:
        parser.error('未找到mysqldump，不能先备份，未清理数据库')
    command = [dumper, '--host=' + args.host, '--port=' + str(args.port), '--user=' + args.user,
               '--single-transaction', '--set-gtid-purged=OFF', '--default-character-set=utf8mb4', args.database]
    result = subprocess.run(command, capture_output=True)
    if result.returncode or not result.stdout:
        parser.error('备份失败，未修改数据库')
    backup.write_bytes(result.stdout)

    teachers = sorted({n.strip() for r in records for n in re.split('[,，;；、]', str(r['raw']['教师名称'])) if n.strip()})
    labs = sorted({s['lab_code'] for r in records for s in r['schedule']})
    passwords = [{'role': '管理员', 'username': 'admin', 'name': '教学管理员', 'password': secrets.token_urlsafe(12)}]
    passwords += [dict(role='教师', username=f'TMP{i:04d}', name=name, password=secrets.token_urlsafe(12),
                       note='系统分配的临时账号，不是学校工号') for i, name in enumerate(teachers, 1)]
    credential_path = output / 'teaching-accounts.local.json'
    if credential_path.exists():
        (output / ('teaching-accounts-before-' + tag + '.json')).write_bytes(credential_path.read_bytes())
    # Create credentials before committing, so a filesystem failure cannot lock out initialized accounts.
    pending_credentials = output / ('teaching-accounts-pending-' + tag + '.json')
    pending_credentials.write_text(json.dumps(passwords, ensure_ascii=False, indent=2), encoding='utf-8')
    connection = pymysql.connect(host=args.host, port=args.port, user=args.user,
                                 password=os.environ['MYSQL_PWD'], database=args.database,
                                 charset='utf8mb4', autocommit=False)
    try:
        with connection.cursor() as cursor:
            # Explicit FK-safe deletion; leave table definitions and source files intact.
            cursor.execute('UPDATE experiment_project SET copied_from_id=NULL')
            for table in ['experiment_project', 'schedule_detail', 'teaching_task_teacher', 'teaching_task',
                          'teaching_import_row', 'teaching_import_batch', 'academic_term', 'academic_year', 'course']:
                cursor.execute('DELETE FROM ' + table)
            cursor.execute('UPDATE shiyanshixinxi SET manager_teacher_id=NULL')
            for table in LEGACY + ['shiyanshixinxi', 'jiaoshi', 'users']:
                cursor.execute('DELETE FROM ' + table)
            cursor.execute("INSERT INTO users (username,password,role) VALUES (%s,%s,%s)",
                           ('admin', bcrypt.hashpw(passwords[0]['password'].encode(), bcrypt.gensalt(10)).decode(), '管理员'))
            teacher_ids = {}
            for account in passwords[1:]:
                cursor.execute('INSERT INTO jiaoshi (gonghao,mima,jiaoshixingming,xueyuan) VALUES (%s,%s,%s,%s)',
                               (account['username'], bcrypt.hashpw(account['password'].encode(), bcrypt.gensalt(10)).decode(),
                                account['name'], '人工智能与大数据学院'))
                teacher_ids[account['name']] = cursor.lastrowid
            lab_ids = {}
            for code in labs:
                cursor.execute('INSERT INTO shiyanshixinxi (shiyanshibianhao,shiyanshimingcheng,shiyanshiguimo,shiyanshiweizhi,shiyanshizhuangtai) VALUES (%s,%s,%s,%s,%s)',
                               (code, code + '实验室', '待完善', code, '使用中'))
                lab_ids[code] = cursor.lastrowid
            term_ids, course_ids = {}, {}
            today = datetime.now().date()
            current_start = today.year if (today.month,today.day) >= (8,31) else today.year-1
            current_year = f'{current_start}-{current_start+1}'
            current_term = 2 if (today.month,today.day) >= (2,1) and (today.month,today.day) < (8,31) else 1
            years = sorted({str(r['raw']['学年']) for r in records} | {current_year})
            for year in years:
                cursor.execute('INSERT INTO academic_year (name,start_year) VALUES (%s,%s)', (year, int(year[:4])))
                year_id = cursor.lastrowid
                numbers = {int(r['raw']['学期']) for r in records if str(r['raw']['学年']) == year}
                if year == current_year: numbers.add(current_term)
                for term in sorted(numbers):
                    start = f'{year[:4]}-08-31' if term == 1 else f'{year[5:]}-02-01'
                    end = f'{year[5:]}-01-31' if term == 1 else f'{year[5:]}-08-30'
                    status = 'OPEN' if year == current_year and term == current_term else 'ARCHIVED'
                    cursor.execute('INSERT INTO academic_term (academic_year_id,term_no,starts_on,ends_on,status) VALUES (%s,%s,%s,%s,%s)',
                                   (year_id, term, start, end, status))
                    term_ids[(year, term)] = cursor.lastrowid
            for record in records:
                raw = record['raw']; code = str(raw['课程号'])
                if code not in course_ids:
                    cursor.execute('INSERT INTO course (course_code,course_name) VALUES (%s,%s)', (code, raw['课程名称']))
                    course_ids[code] = cursor.lastrowid
            cursor.execute('INSERT INTO teaching_import_batch (file_sha256,file_name,parser_version,row_count) VALUES (%s,%s,%s,%s)',
                           (report['sha256'], report['file'], PARSER_VERSION, len(records)))
            batch_id = cursor.lastrowid
            task_columns = ['task_code','term_id','course_id','source_import_row_id','course_name_snapshot','department_name',
                'credits','class_composition','major_composition','class_size','enrollment_count','planned_lab_hours','weekly_hours',
                'original_week_range','scheduled_week_range','start_week','end_week','course_ends_at','course_weekly_hours_text',
                'teacher_names_original','locations_original','schedule_original','default_lab_id']
            insert_task = 'INSERT INTO teaching_task (' + ','.join(task_columns) + ') VALUES (' + ','.join(['%s']*len(task_columns)) + ')'
            for record in records:
                raw = record['raw']
                issues = [i for i in record['issues'] if i != 'TEACHER_ID_MAPPING_REQUIRED'] + ['TEMPORARY_TEACHER_ACCOUNT']
                cursor.execute('INSERT INTO teaching_import_row (batch_id,sheet_name,source_row,raw_data,parsed_schedule,issues,status) VALUES (%s,%s,%s,%s,%s,%s,%s)',
                               (batch_id, record['sheet'], record['source_row'], json.dumps(raw,ensure_ascii=False),
                                json.dumps(record['schedule'],ensure_ascii=False), json.dumps(issues,ensure_ascii=False), 'PROMOTED'))
                source_id = cursor.lastrowid
                values = [f"INIT-{report['sha256'][:8]}-{record['source_row']}", term_ids[(str(raw['学年']),int(raw['学期']))],
                    course_ids[str(raw['课程号'])], source_id, raw['课程名称'], raw['开课学院'], raw['学分'], raw['教学班组成'],
                    raw['专业组成'], raw['教学班人数'], raw['选课人数'], raw['课程实验总学时'], raw['周学时'], raw['起始结束周'],
                    raw['排课起始结束周'], raw['起始周'], raw['结束周'], str(raw['课程结束时间']).strip(), raw['课程周学时'],
                    raw['教师名称'], raw['教学地点'], raw['上课时间'], lab_ids[record['schedule'][0]['lab_code']]]
                cursor.execute(insert_task, values)
                task_id = cursor.lastrowid
                for name in set(n.strip() for n in re.split('[,，;；、]', str(raw['教师名称']))):
                    cursor.execute('INSERT INTO teaching_task_teacher (task_id,teacher_id) VALUES (%s,%s)', (task_id,teacher_ids[name]))
                cursor.executemany('INSERT INTO schedule_detail (task_id,lab_id,teaching_week,weekday,period_start,period_end,hours,source_segment) VALUES (%s,%s,%s,%s,%s,%s,%s,%s)',
                                  [(task_id,lab_ids[s['lab_code']],s['week'],s['weekday'],s['period_start'],s['period_end'],s['hours'],s['source_segment']) for s in record['schedule']])
            for table in LEGACY:
                cursor.execute('SELECT COUNT(*) FROM ' + table)
                assert cursor.fetchone()[0] == 0, table
            counts = {}
            for table in ['users','jiaoshi','shiyanshixinxi','course','academic_term','teaching_task','schedule_detail','experiment_project']:
                cursor.execute('SELECT COUNT(*) FROM ' + table)
                counts[table] = cursor.fetchone()[0]
            assert counts['teaching_task'] == len(records)
            assert counts['schedule_detail'] == report['expanded_schedule_rows']
        connection.commit()
        pending_credentials.replace(credential_path)
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()
    summary = dict(database=args.database, backup=str(backup), credentials_file=str(credential_path),
                   counts=counts, legacy_demo_rows=0, status='INITIALIZED',
                   note='Historical timetable imported row by row, temporary teacher identities and data discrepancies remain explicitly marked.')
    (root/'docs/database/reports/teaching-initialization.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
