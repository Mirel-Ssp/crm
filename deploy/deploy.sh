#!/usr/bin/env bash
# =====================================================================
# CRM 一键部署脚本（在 deploy/ 目录执行：./deploy.sh）
# 前置：已安装 docker 与 docker compose 插件；已准备 deploy/.env
# 流程：密钥检查 -> 镜像构建 -> 启动（Flyway 自动迁移 V1~V15） -> 健康检查
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"

# ---- 1. .env 检查（缺失或弱密钥直接中止，防带占位符上线） ----
if [ ! -f .env ]; then
  echo "[ERROR] deploy/.env 不存在：先执行 cp .env.example .env 并填入强密钥"
  exit 1
fi
if grep -q "change-me" .env; then
  echo "[ERROR] deploy/.env 仍含 change-me 占位密钥，请全部替换为强随机值后重试"
  exit 1
fi

# ---- 2. docker 环境检查 ----
command -v docker >/dev/null 2>&1 || { echo "[ERROR] 未安装 docker"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "[ERROR] 缺少 docker compose 插件"; exit 1; }

# ---- 3. 构建并启动 ----
echo "[1/3] 构建镜像并启动（首次约 5~10 分钟）..."
docker compose up -d --build

# ---- 4. 健康检查（经 80 网关校验完整链路：nginx -> backend -> PG） ----
echo "[2/3] 等待后端就绪（Flyway 迁移随首启自动执行）..."
ok=0
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1/api/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 2
done
if [ "$ok" != "1" ]; then
  echo "[ERROR] 后端 120s 内未就绪，排查：docker compose logs backend"
  exit 1
fi

# ---- 5. 收尾输出 ----
echo "[3/3] 部署完成"
docker compose ps
echo
echo "访问入口   : http://<服务器公网IP>/（默认账号 admin/admin123，登录后立即修改）"
echo "健康检查   : curl http://127.0.0.1/api/health"
echo "日志排查   : docker compose logs -f backend"
echo "数据库备份 : ./backup.sh（建议 crontab 每日执行）"
