#!/usr/bin/env python3
"""Read-only KR1 snapshot and transactional one-time import into an EMPTY KR3 database."""
import argparse
import json
from pathlib import Path
import secrets
import subprocess

PSQL = 'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'


def execute(container, sql):
    result = subprocess.run(['docker', 'exec', '-i', container, 'sh', '-c', PSQL],
                            input=sql, text=True, capture_output=True)
    if result.returncode:
        # PostgreSQL diagnostics can contain source data. Do not expose them automatically.
        raise SystemExit('PostgreSQL отклонил операцию. Импорт отменён; проверьте схему и пустоту целевой БД.')
    return result.stdout.strip()


def snapshot(container, destination):
    sql = """
      BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
      SET LOCAL TIME ZONE 'UTC';
      SELECT jsonb_build_object('format',1,'timezone','UTC',
        'users',COALESCE((SELECT jsonb_agg(to_jsonb(u) ORDER BY id) FROM users u),'[]'::jsonb),
        'orders',COALESCE((SELECT jsonb_agg(to_jsonb(o) ORDER BY id) FROM
          (SELECT id,title,description,category,budget::text,deadline,status,
                  customer_id,freelancer_id,created_at FROM orders) o),'[]'::jsonb));
      COMMIT;
    """
    data = json.loads(execute(container, sql))
    # Never overwrite another snapshot; contains participant data, keep private.
    with destination.open('x', encoding='utf-8') as stream:
        destination.chmod(0o600)
        json.dump(data, stream, ensure_ascii=False)
    print(f'Снимок: {len(data["users"])} пользователей, {len(data["orders"])} заказов → {destination}')


def restore(container, source):
    data = json.loads(source.read_text())
    if data.get('format') != 1 or data.get('timezone') != 'UTC':
        raise SystemExit('Неизвестный формат snapshot')
    payload = json.dumps(data, ensure_ascii=False)
    marker = '$snapshot_' + secrets.token_hex(16) + '$'
    sql = """
      BEGIN;
      SET LOCAL TIME ZONE 'UTC';
      LOCK TABLE users, orders IN EXCLUSIVE MODE;
      DO $$ BEGIN
        IF EXISTS(SELECT 1 FROM users) OR EXISTS(SELECT 1 FROM orders) THEN
          RAISE EXCEPTION 'Import requires empty target';
        END IF;
        IF NOT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='3' AND success) THEN
          RAISE EXCEPTION 'Run backend migrations first';
        END IF;
      END $$;
      CREATE TEMP TABLE legacy_snapshot(data jsonb) ON COMMIT DROP;
    """ + 'INSERT INTO legacy_snapshot VALUES (' + marker + payload + marker + '::jsonb);' + """
      INSERT INTO users(id,name,email,role,rating,registered_at)
        SELECT x.id,x.name,x.email,x.role,x.rating,x.registered_at FROM legacy_snapshot s,
        jsonb_to_recordset(s.data->'users') AS x(id bigint,name text,email text,role text,rating numeric,registered_at date);
      INSERT INTO orders(id,title,description,category_id,code,budget,deadline,status,
                         customer_id,freelancer_id,created_at,updated_at,public_id,urgent,archived,version)
        SELECT x.id,x.title,x.description,c.id,'LEGACY-'||x.id,x.budget,x.deadline,x.status,
               x.customer_id,x.freelancer_id,x.created_at AT TIME ZONE 'UTC',
               x.created_at AT TIME ZONE 'UTC',gen_random_uuid(),false,false,0
        FROM legacy_snapshot s,
        jsonb_to_recordset(s.data->'orders') AS x(id bigint,title text,description text,category text,budget numeric,
             deadline date,status text,customer_id bigint,freelancer_id bigint,created_at timestamp)
        LEFT JOIN categories c ON c.code=x.category;
      DO $$ BEGIN
        IF (SELECT count(*) FROM users) <> (SELECT jsonb_array_length(data->'users') FROM legacy_snapshot)
          OR (SELECT count(*) FROM orders) <> (SELECT jsonb_array_length(data->'orders') FROM legacy_snapshot) THEN
          RAISE EXCEPTION 'Count mismatch';
        END IF;
        IF EXISTS(
          SELECT 1 FROM legacy_snapshot s,
          jsonb_to_recordset(s.data->'orders') AS x(id bigint,budget numeric,deadline date,
             customer_id bigint,freelancer_id bigint,created_at timestamp,category text)
          JOIN orders o ON o.id=x.id JOIN categories c ON c.id=o.category_id
          WHERE o.budget IS DISTINCT FROM x.budget OR o.deadline IS DISTINCT FROM x.deadline
            OR o.customer_id IS DISTINCT FROM x.customer_id OR o.freelancer_id IS DISTINCT FROM x.freelancer_id
            OR o.created_at IS DISTINCT FROM (x.created_at AT TIME ZONE 'UTC') OR c.code IS DISTINCT FROM x.category
        ) THEN RAISE EXCEPTION 'Data mismatch'; END IF;
      END $$;
      SELECT setval(pg_get_serial_sequence('users','id'), GREATEST(COALESCE(max(id),1),1), count(*)>0) FROM users;
      SELECT setval(pg_get_serial_sequence('orders','id'), GREATEST(COALESCE(max(id),1),1), count(*)>0) FROM orders;
      COMMIT;
      SELECT jsonb_build_object('users',(SELECT count(*) FROM users),'orders',(SELECT count(*) FROM orders),
        'budget',(SELECT COALESCE(sum(budget),0)::text FROM orders),'publicIds',(SELECT count(DISTINCT public_id) FROM orders));
    """
    output = execute(container, sql)
    result = json.loads(output.splitlines()[-1])
    print(json.dumps(result, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='operation', required=True)
    export = sub.add_parser('snapshot')
    export.add_argument('--source-container', required=True)
    export.add_argument('--out', type=Path, required=True)
    load = sub.add_parser('import')
    load.add_argument('--target-container', required=True)
    load.add_argument('--snapshot', type=Path, required=True)
    args = parser.parse_args()
    if args.operation == 'snapshot':
        snapshot(args.source_container, args.out)
    else:
        restore(args.target_container, args.snapshot)


if __name__ == '__main__':
    main()
