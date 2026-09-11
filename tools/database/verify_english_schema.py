"""Verify English schema migration in disposable MySQL databases.

Requires MYSQL_PWD, mysql and PyMySQL. --legacy-ref can test an actual earlier
Git revision's T132.sql and seed; otherwise reconstructs the legacy names.
No existing database is modified. --backend-tests also runs Maven DB tests.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid

import pymysql

ROOT = Path(__file__).resolve().parents[2]
NAMES = json.loads((Path(__file__).with_name('english_schema_names.json')).read_text(encoding='utf-8'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--user', default='root')
    parser.add_argument('--legacy-ref')
    parser.add_argument('--backend-tests', action='store_true')
    args = parser.parse_args()
    mysql = shutil.which('mysql')
    if not mysql or 'MYSQL_PWD' not in os.environ:
        parser.error('mysql and MYSQL_PWD are required')
    connection = pymysql.connect(host=args.host, port=args.port, user=args.user,
                                 password=os.environ['MYSQL_PWD'], charset='utf8mb4', autocommit=True)
    command = [mysql, '--host='+args.host, '--port='+str(args.port), '--user='+args.user,
               '--default-character-set=utf8mb4', '--batch']
    prefix = 'english_schema_verify_'+uuid.uuid4().hex[:12]
    created = []

    def script(sql, database):
        result = subprocess.run(command+['--database='+database], input=sql,
                                encoding='utf-8', capture_output=True)
        if result.returncode:
            raise RuntimeError(result.stderr[-2000:])

    def query(sql, *params):
        with connection.cursor() as cursor:
            cursor.execute(sql, params)
            return cursor.fetchall()

    def snapshot(database, legacy=False):
        result = {}
        for (table,) in query('SELECT table_name FROM information_schema.tables WHERE table_schema=%s', database):
            target = NAMES['tables'].get(table, table) if legacy else table
            with connection.cursor(pymysql.cursors.DictCursor) as cursor:
                cursor.execute(f'SELECT * FROM `{database}`.`{table}`')
                rows = []
                for row in cursor.fetchall():
                    row = {NAMES['columns'].get(target, {}).get(k, k) if legacy else k: v for k, v in row.items()}
                    if target == 'token' and legacy:
                        row['tablename'] = NAMES['tables'].get(row['tablename'], row['tablename'])
                    rows.append(json.dumps(row, sort_keys=True, ensure_ascii=False, default=str))
                result[target] = sorted(rows)
        return result

    def metadata(database):
        return query('SELECT table_name,column_name,column_type,is_nullable,column_default,extra,column_comment,collation_name '
                     'FROM information_schema.columns WHERE table_schema=%s ORDER BY table_name,ordinal_position', database)

    try:
        fresh, migrated = prefix+'_fresh', prefix+'_migrated'
        for database in (fresh, migrated):
            query(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4')
            created.append(database)
        schema = (ROOT/'T132.sql').read_text(encoding='utf-8')
        seed = (ROOT/'database/003_teaching_seed.sql').read_text(encoding='utf-8')
        migration = (ROOT/'database/004_english_schema.sql').read_text(encoding='utf-8')
        script(schema, fresh)
        script(seed, fresh)
        expected_metadata = metadata(fresh)
        assert len(expected_metadata) == 117
        assert all(row[6] for row in expected_metadata), 'Every column must have a comment'
        assert len(query('SELECT table_name FROM information_schema.tables WHERE table_schema=%s AND table_comment<>%s', fresh, '')) == 13
        assert not any(row[0] in NAMES['tables'] or row[1] in NAMES['columns'].get(row[0], {}) for row in expected_metadata)

        if args.legacy_ref:
            def historical(file):
                return subprocess.check_output(['git', 'show', args.legacy_ref+':'+file], cwd=ROOT).decode('utf-8')
            script(historical('T132.sql'), migrated)
            script(historical('database/003_teaching_seed.sql'), migrated)
        else:
            script(schema, migrated)
            script(seed, migrated)
            for old, table in NAMES['tables'].items():
                for old_column, column in NAMES['columns'][table].items():
                    script(f'ALTER TABLE `{table}` RENAME COLUMN `{column}` TO `{old_column}`;', migrated)
                index = 'gonghao' if table == 'teacher' else 'shiyanshibianhao'
                script(f'ALTER TABLE `{table}` RENAME INDEX `{NAMES["columns"][table][index]}` TO `{index}`;'
                       f'RENAME TABLE `{table}` TO `{old}`;', migrated)

        # Exercise persisted sessions and both project teacher foreign keys, absent in the seed.
        query(f"INSERT INTO `{migrated}`.token(userid,username,tablename,role,token) VALUES (27,'TMP0001','jiaoshi','教师','migration-test-token')")
        query(f"INSERT INTO `{migrated}`.experiment_project(task_id,project_code,school_code,name,category_code,type_code,discipline_code,requirement_code,participant_type_code,group_size,hours,created_by_teacher_id,updated_by_teacher_id) "
              f"SELECT MIN(id),'MIGRATION-TEST','TEST','迁移验证','1','2','0809','1','3',2,2,27,27 FROM `{migrated}`.teaching_task")
        before = snapshot(migrated, legacy=True)
        script(migration, migrated)
        assert snapshot(migrated) == before, 'Migration must preserve every row and credential'
        assert metadata(migrated) == expected_metadata, 'Fresh and migrated column definitions must match'
        fk = ('SELECT table_name,column_name,referenced_table_name,referenced_column_name '
              'FROM information_schema.key_column_usage WHERE table_schema=%s AND referenced_table_name IS NOT NULL '
              'ORDER BY table_name,column_name,referenced_table_name')
        assert query(fk, migrated) == query(fk, fresh), 'All foreign keys must match'
        indexes = ('SELECT table_name,index_name,non_unique,seq_in_index,column_name '
                   'FROM information_schema.statistics WHERE table_schema=%s ORDER BY table_name,index_name,seq_in_index')
        assert query(indexes, migrated) == query(indexes, fresh), 'All indexes must match'
        script(migration, migrated)
        script(migration, fresh)
        assert snapshot(migrated) == before and metadata(migrated) == expected_metadata
        assert metadata(fresh) == expected_metadata
        print('PASS: 13 English tables, 117 Chinese column comments; fresh import; migration preserves every row, credential, foreign key and index; repeat execution.', flush=True)
        if args.backend_tests:
            env = dict(os.environ, TEACHING_DB_TEST='1', TEACHING_TEST_DB_USER=args.user,
                       TEACHING_TEST_DB_PASSWORD=os.environ['MYSQL_PWD'],
                       TEACHING_TEST_DB_URL=f'jdbc:mysql://{args.host}:{args.port}/{migrated}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai')
            env.pop('TEACHING_AI_DB_TEST', None)
            subprocess.run([shutil.which('mvn'), '-q', 'package'], cwd=ROOT/'back', env=env, check=True)
            print('PASS: backend tests against the migrated database.', flush=True)
    finally:
        for database in reversed(created):
            assert re.fullmatch(re.escape(prefix)+r'_(fresh|migrated)', database)
            query(f'DROP DATABASE `{database}`')
        connection.close()


if __name__ == '__main__':
    main()
