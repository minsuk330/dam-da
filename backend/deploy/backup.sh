#!/usr/bin/env bash
# PostgreSQL 백업(#183). backend/deploy에서 compose로 띄운 postgres를 pg_dump로 떠서 압축해 둔다.
# cron 예: 0 4 * * * cd ~/apps/khack/backend/deploy && ./backup.sh >> backups/backup.log 2>&1
#
# 설정(backend/deploy/.env 또는 환경 변수)
#   BACKUP_DIR            보관 폴더. 기본 ./backups
#   BACKUP_KEEP_DAYS      로컬 보관 일수. 기본 7
#   BACKUP_AZURE_CONTAINER, AZURE_STORAGE_CONNECTION_STRING
#                         둘 다 있으면 az CLI로 Azure Blob에도 올린다(VM 밖 보관). 없으면 로컬에만 둔다.
#   BACKUP_PING_URL       성공하면 GET, 실패하면 <URL>/fail을 GET한다(예: healthchecks.io). 없으면 건너뛴다.
# 백업에는 대화 원문이 들어 있다. 폴더 권한은 소유자만 읽게 한다.
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

BACKUP_DIR="${BACKUP_DIR:-./backups}"
BACKUP_KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
DB_NAME="${DB_NAME:-review}"
DB_USERNAME="${DB_USERNAME:-review}"

ping() {
  if [ -n "${BACKUP_PING_URL:-}" ]; then
    curl -fsS -m 10 --retry 3 "${BACKUP_PING_URL}$1" > /dev/null || true
  fi
}

umask 077
mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/${DB_NAME}-$(date +%Y%m%d-%H%M%S).sql.gz"
# 실패하면 반쯤 쓴 파일을 지우고 감시 서비스에 알린다.
trap 'rm -f "$file.tmp"; ping /fail' ERR

# --clean --if-exists: 같은 DB에 덮어 복원할 수 있게 DROP을 함께 담는다.
docker compose exec -T postgres pg_dump -U "$DB_USERNAME" -d "$DB_NAME" --clean --if-exists --no-owner \
  | gzip -9 > "$file.tmp"
mv "$file.tmp" "$file"
echo "$(date '+%F %T') 백업 $file ($(du -h "$file" | cut -f1))"

if [ -n "${BACKUP_AZURE_CONTAINER:-}" ] && [ -n "${AZURE_STORAGE_CONNECTION_STRING:-}" ]; then
  az storage blob upload --only-show-errors --overwrite \
    --container-name "$BACKUP_AZURE_CONTAINER" --name "$(basename "$file")" --file "$file"
  echo "$(date '+%F %T') Azure Blob 업로드 $BACKUP_AZURE_CONTAINER/$(basename "$file")"
fi

find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.gz" -type f -mtime +"$BACKUP_KEEP_DAYS" -print -delete
ping ""
