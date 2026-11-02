#!/usr/bin/env bash
# 백업 복원 시험(#183). 백업 파일을 같은 postgres 안의 임시 DB에 복원해 테이블과 행 수를 보여 주고 지운다.
# 운영 DB는 건드리지 않는다.
# 사용: ./restore-check.sh [백업 파일]   (생략하면 가장 최근 백업)
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

BACKUP_DIR="${BACKUP_DIR:-./backups}"
DB_NAME="${DB_NAME:-review}"
DB_USERNAME="${DB_USERNAME:-review}"
CHECK_DB="${DB_NAME}_restore_check"

file="${1:-$(ls -t "$BACKUP_DIR"/"${DB_NAME}"-*.sql.gz 2>/dev/null | head -1)}"
if [ -z "$file" ] || [ ! -f "$file" ]; then
  echo "백업 파일이 없습니다: ${file:-$BACKUP_DIR}" >&2
  exit 1
fi

psql() {
  docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$DB_USERNAME" "$@"
}

cleanup() {
  psql -d "$DB_NAME" -qc "drop database if exists \"$CHECK_DB\"" > /dev/null
}
trap cleanup EXIT

cleanup
psql -d "$DB_NAME" -qc "create database \"$CHECK_DB\""
gunzip -c "$file" | psql -d "$CHECK_DB" -q > /dev/null
echo "복원 $file → $CHECK_DB"
psql -d "$CHECK_DB" -Atc "select relname || ' ' || n_live_tup from pg_stat_user_tables order by relname"
psql -d "$CHECK_DB" -Atc "select '마이그레이션 ' || coalesce(max(version), '-') from flyway_schema_history" 2>/dev/null || true
echo "복원 시험 끝. 임시 DB를 지웁니다."
