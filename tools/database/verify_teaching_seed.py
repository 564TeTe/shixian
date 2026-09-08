"""Import the published SQL in isolated MySQL databases and check actual login.

Requires MYSQL_PWD, bootstrap-requirements.txt, mysql, java and a built backend JAR.
Never changes the source database. Temporary databases and server are removed on exit.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import uuid

import bcrypt
import pymysql

from export_teaching_seed import FIELDS, INITIAL_ADMIN_PASSWORD, LEGACY, ROOT


EXPECTED = {'users': 1, 'jiaoshi': 79, 'shiyanshixinxi': 14, 'course': 93,
            'teaching_task': 255, 'schedule_detail': 2208, 'experiment_project': 0,
            'teaching_import_batch': 1, 'teaching_import_row': 255}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--source-database', default='t132')
    parser.add_argument('--mysql', default=shutil.which('mysql'))
    parser.add_argument('--java', default=shutil.which('java'))
    args = parser.parse_args()
    if not args.mysql or not args.java or 'MYSQL_PWD' not in os.environ or not re.fullmatch('[A-Za-z0-9_]+', args.source_database):
        parser.error('mysql, java, MYSQL_PWD and valid source database are required')
    jar = ROOT/'back/target/springboote51e2-0.0.1-SNAPSHOT.jar'
    if not jar.exists():
        parser.error('Build the backend with mvn package first')
    seed = (ROOT/'database/003_teaching_seed.sql').read_text(encoding='utf-8')
    schema = (ROOT/'T132.sql').read_text(encoding='utf-8')
    connection = pymysql.connect(host=args.host,port=args.port,user=args.user,
        password=os.environ['MYSQL_PWD'],charset='utf8mb4',autocommit=True)
    mysql = [args.mysql, '--host='+args.host, '--port='+str(args.port), '--user='+args.user,
             '--default-character-set=utf8mb4', '--batch']
    scratch = 'teaching_seed_verify_' + uuid.uuid4().hex[:10]
    databases = [scratch, scratch+'_rollback']
    created = []
    server = None
    log = None
    checks = []

    def execute_script(sql, database, error=None):
        result = subprocess.run(mysql+['--database='+database],input=sql,encoding='utf-8',capture_output=True)
        if error:
            assert result.returncode != 0 and error in result.stderr, 'Expected SQL rejection: '+str(error)
        elif result.returncode:
            raise RuntimeError(result.stderr[-1000:])

    def counts(database):
        with connection.cursor() as cursor:
            result = {}
            for table in [*FIELDS, 'users', *LEGACY]:
                cursor.execute(f'SELECT COUNT(*) FROM `{database}`.`{table}`')
                result[table] = cursor.fetchone()[0]
            return result

    try:
        # Secret comparisons occur in memory; neither hashes nor tokens are printed.
        with connection.cursor() as cursor:
            for table, column in [('users','password'),('jiaoshi','mima'),('token','token')]:
                cursor.execute(f'SELECT `{column}` FROM `{args.source_database}`.`{table}`')
                assert all(not value or value not in seed for (value,) in cursor.fetchall()), 'Local credential found in seed'
            for database in databases:
                cursor.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4')
                created.append(database)
        checks.append('No source password hashes or active tokens in shared SQL')
        execute_script(schema, scratch)
        execute_script(seed, scratch)
        actual = counts(scratch)
        assert all(actual[k] == v for k,v in EXPECTED.items()), actual
        assert all(actual[table] == 0 for table in LEGACY)
        with connection.cursor() as cursor:
            cursor.execute(f'SELECT password FROM `{scratch}`.users WHERE username=%s', ('admin',))
            assert bcrypt.checkpw(INITIAL_ADMIN_PASSWORD.encode(), cursor.fetchone()[0].encode())
            cursor.execute(f'SELECT COUNT(*) FROM `{scratch}`.teaching_import_row WHERE JSON_VALID(raw_data) AND JSON_VALID(issues)')
            assert cursor.fetchone()[0] == 255
            cursor.execute(f'SELECT COUNT(*) FROM `{scratch}`.jiaoshi WHERE dianhua IS NOT NULL OR touxiang IS NOT NULL')
            assert cursor.fetchone()[0] == 0
            cursor.execute(f'SELECT SUM(s.hours*t.enrollment_count) FROM `{scratch}`.schedule_detail s JOIN `{scratch}`.teaching_task t ON t.id=s.task_id')
            shared_hours = str(cursor.fetchone()[0])
            cursor.execute(f'SELECT SUM(s.hours*t.enrollment_count) FROM `{args.source_database}`.schedule_detail s JOIN `{args.source_database}`.teaching_task t ON t.id=s.task_id')
            assert shared_hours == str(cursor.fetchone()[0])
        checks.append('Empty database import: expected counts, valid JSON, fresh BCrypt admin and matching person-hours')
        execute_script(seed, scratch, error='requires empty tables')
        assert counts(scratch) == actual
        checks.append('Repeated import rejected without changing records or resetting accounts')
        execute_script(schema, databases[1])
        broken = seed.replace('  COMMIT;', '  INSERT INTO teaching_task_teacher(task_id,teacher_id) VALUES(-1,-1);\n  COMMIT;')
        execute_script(broken, databases[1], error='1452')
        assert all(value == 0 for value in counts(databases[1]).values())
        checks.append('Foreign-key failure rolls back the entire data import')

        with socket.socket() as free_port:
            free_port.bind(('127.0.0.1', 0))
            port = free_port.getsockname()[1]
        runtime = ROOT/'database/generated'
        runtime.mkdir(exist_ok=True)
        log = (runtime/'seed-verification-backend.log').open('wb')
        env = os.environ.copy()
        env.update(SERVER_ADDRESS='127.0.0.1', SERVER_PORT=str(port),
            SPRING_DATASOURCE_URL=f'jdbc:mysql://{args.host}:{args.port}/{scratch}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai',
            SPRING_DATASOURCE_USERNAME=args.user, SPRING_DATASOURCE_PASSWORD=os.environ['MYSQL_PWD'])
        for key in list(env):
            if key.startswith('TEACHING_AI_'):
                del env[key]
        server = subprocess.Popen([args.java,'-Dfile.encoding=UTF-8','-jar',str(jar)],env=env,
            cwd=ROOT/'back',stdout=log,stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        base = f'http://127.0.0.1:{port}/springboote51e2'

        def api(path, token=None, body=None, form=False):
            headers = {'Token': token} if token else {}
            data = None
            if body is not None:
                headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
                data = (urlencode(body) if form else json.dumps(body)).encode()
            with urlopen(Request(base+path,data,headers),timeout=10) as response:
                result = json.load(response)
            assert result['code'] == 0, 'HTTP business error: '+str(result.get('code'))
            return result

        deadline = time.monotonic()+55
        while True:
            if server.poll() is not None:
                raise RuntimeError('Temporary backend exited; inspect local seed-verification-backend.log')
            try:
                login = api('/users/login',body={'username':'admin','password':INITIAL_ADMIN_PASSWORD},form=True)
                break
            except OSError:
                if time.monotonic() > deadline:
                    raise RuntimeError('Temporary backend startup timed out')
                time.sleep(0.5)
        token = login['token']
        dashboard = api('/teaching/dashboard',token)['data']
        assert dashboard['counts']['tasks'] == 255 and dashboard['counts']['projects'] == 0
        assert api('/teaching/tasks',token)['data']['total'] == 255
        assert len(api('/teaching/reports',token)['data']['labs']) == 14
        teacher = api('/teaching/teachers',token)['data']['list'][0]
        reset = api('/teaching/teachers/'+str(teacher['id'])+'/reset-password',token,body={})['data']
        teacher_login = api('/jiaoshi/login',body={'username':teacher['gonghao'],'password':reset['password']},form=True)
        teacher_tasks = api('/teaching/tasks',teacher_login['token'])['data']
        assert teacher_tasks['total'] > 0
        checks.append('Real backend: new admin login, 255 tasks, 14 lab reports, teacher password reset and login')
        report = {'status':'PASS','counts':actual,'person_hours':shared_hours,'checks':checks,
                  'source_database_modified':False,'temporary_databases_removed_on_exit':True}
    finally:
        if server is not None and server.poll() is None:
            server.terminate()
            try:
                server.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=10)
        if log:
            log.close()
        with connection.cursor() as cursor:
            for database in reversed(created):
                cursor.execute(f'DROP DATABASE `{database}`')
        connection.close()
    target = ROOT/'docs/database/reports/teaching-seed-verification.json'
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
