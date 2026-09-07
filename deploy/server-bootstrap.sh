#!/usr/bin/env bash
# =====================================================================
# CRM 服务器端一键部署脚本（在阿里云服务器上执行）
# 用法：bash server-bootstrap.sh
# 功能：装 Docker → clone 代码 → 生成密钥 → 部署 → 健康检查
# =====================================================================
set -euo pipefail

REPO_URL="https://github.com/Mirel-Ssp/crm.git"
INSTALL_DIR="/opt/crm"
DEPLOY_DIR="$INSTALL_DIR/customer_management/deploy"

echo "========================================"
echo "  CRM 系统部署脚本（阿里云 ACL 3）"
echo "========================================"

# ---- 1. 安装 Docker ----
echo ""
echo "[1/6] 检查 Docker..."
if command -v docker &>/dev/null && docker compose version &>/dev/null; then
    echo "  Docker 已安装，跳过"
else
    echo "  安装 Docker..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable --now docker
    echo "  Docker 安装完成"
fi
docker --version
docker compose version

# ---- 2. 拉取代码 ----
echo ""
echo "[2/6] 拉取代码..."
if [ -d "$INSTALL_DIR/.git" ]; then
    echo "  目录已存在，拉取最新代码..."
    cd "$INSTALL_DIR"
    git pull origin main || true
else
    rm -rf "$INSTALL_DIR"
    git clone "$REPO_URL" "$INSTALL_DIR"
fi
cd "$INSTALL_DIR/customer_management"
echo "  代码就绪: $(git log --oneline -1)"

# ---- 3. 生成强随机密钥 ----
echo ""
echo "[3/6] 生成生产密钥..."
cd "$DEPLOY_DIR"
if [ -f .env ]; then
    echo "  .env 已存在，跳过生成（如需重新部署请先 rm .env）"
else
    PG_PASS=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
    REDIS_PASS=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
    MINIO_PASS=$(openssl rand -base64 24 | tr -d '/+=' | head -c 32)
    JWT_SECRET=$(openssl rand -base64 48 | tr -d '/+=' | head -c 64)

    cat > .env << EOF
# PostgreSQL
CRM_PG_PASSWORD=${PG_PASS}
# Redis
CRM_REDIS_PASSWORD=${REDIS_PASS}
# MinIO
CRM_MINIO_PASSWORD=${MINIO_PASS}
# JWT 签名密钥（>=32 字节）
CRM_JWT_SECRET=${JWT_SECRET}
# 跨域（同源网关部署留空）
CRM_CORS_ALLOWED_ORIGINS=
EOF
    chmod 600 .env
    echo "  密钥已生成并写入 .env（权限 600）"
fi

# ---- 4. 脚本权限 ----
echo ""
echo "[4/6] 设置脚本权限..."
chmod +x deploy.sh backup.sh 2>/dev/null || true

# ---- 5. 构建并启动 ----
echo ""
echo "[5/6] 构建镜像并启动（首次约 5~10 分钟）..."
docker compose up -d --build 2>&1 || {
    echo "  [ERROR] 构建失败，查看日志：docker compose logs"
    exit 1
}

# ---- 6. 健康检查 ----
echo ""
echo "[6/6] 等待后端就绪（Flyway 迁移 + Spring Boot 启动）..."
OK=0
for i in $(seq 1 90); do
    if curl -fsS http://127.0.0.1/api/health >/dev/null 2>&1; then
        OK=1
        break
    fi
    sleep 2
    echo -n "."
done
echo ""

if [ "$OK" = "1" ]; then
    echo ""
    echo "========================================"
    echo "  部署成功！"
    echo "========================================"
    echo ""
    docker compose ps
    echo ""
    echo "访问地址 : http://$(curl -s http://checkip.amazonaws.com 2>/dev/null || echo '服务器IP')/"
    echo "账号     : admin / admin123（登录后立即改密）"
    echo "健康检查 : curl http://127.0.0.1/api/health"
    echo "查看日志 : docker compose logs -f backend"
    echo "数据库备份: ./backup.sh（建议 crontab 每日执行）"
    echo ""
    echo "密钥文件 : $DEPLOY_DIR/.env（请妥善备份）"
else
    echo ""
    echo "[ERROR] 后端 180s 内未就绪"
    echo "排查命令："
    echo "  docker compose logs backend"
    echo "  docker compose ps"
    exit 1
fi
