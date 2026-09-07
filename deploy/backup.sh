#!/usr/bin/env bash
# =====================================================================
# CRM 数据库备份脚本（在 deploy/ 目录执行：./backup.sh [保留天数，默认 7]）
# 产物：deploy/backups/crm-db-<时间戳>.sql.gz，超期自动清理
# 建议 crontab（每日 03:30）：
#   30 3 * * * /opt/crm/deploy/backup.sh >> /var/log/crm-backup.log 2>&1
# 恢复：gunzip -c backups/crm-db-<时间戳>.sql.gz | docker compose exec -T postgres psql -U crm -d crm
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"

KEEP_DAYS="${1:-7}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p backups

echo "[backup] pg_dump 开始..."
docker compose exec -T postgres pg_dump -U crm -d crm | gzip > "backups/crm-db-$STAMP.sql.gz"

SIZE=$(du -h "backups/crm-db-$STAMP.sql.gz" | cut -f1)
echo "[backup] 完成 backups/crm-db-$STAMP.sql.gz ($SIZE)"

# 保留策略：删除超期备份
find backups -name 'crm-db-*.sql.gz' -type f -mtime +"$KEEP_DAYS" -delete
echo "[backup] 已清理 ${KEEP_DAYS} 天前的旧备份"
