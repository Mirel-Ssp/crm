# CRM 生产部署指南

## 一、部署架构

```
                    ┌──────────────────────────── docker 内网 ────────────────────────────┐
 公网/内网用户      │  ┌──────────┐  /api/ /ws/   ┌──────────┐        ┌──────────────┐    │
 ──────────────►   │  │ frontend │ ───反代────►  │ backend  │ ─────► │ postgres 16.9│    │
   :80 (唯一入口)  │  │ nginx:80 │  同源网关     │ :8080    │        │ (仅内网可达) │    │
                   │  └──────────┘               └──────────┘        ├──────────────┤    │
                   │        静态资源 frontend/dist     │              │ redis 7      │    │
                   │                                │              ├──────────────┤    │
                   │                                └────────────► │ minio        │    │
                   │                                               └──────────────┘    │
                   └────────────────────────────────────────────────────────────────────┘
                     后端 8080 仅绑定 127.0.0.1（本机健康检查用，不对公网暴露）
```

- **frontend（nginx）**：唯一公网入口（80 端口），托管前端静态资源并同源反代 `/api/`、`/ws/`，免跨域配置
- **backend（Spring Boot）**：prod profile，Redis 缓存 + MinIO 存储；Flyway 迁移（V1~V15）随启动自动执行
- **中间件**：postgres / redis / minio 均不暴露公网端口，仅 compose 内网可达

## 二、前置条件

| 项 | 要求 |
|---|---|
| 服务器 | Linux x86_64，2C4G 起步（建议 4C8G），磁盘 40G+ |
| 软件 | Docker 24+、docker compose 插件 v2 |
| 端口 | 80（入口）对外；8080 仅本机回环 |
| 密钥 | 4 个强随机值（生成：`openssl rand -base64 32/48`） |

## 三、首次部署

```bash
# 1. 拉取代码
git clone <repo> crm && cd crm/customer_management/deploy

# 2. 准备环境变量（4 个密钥全部替换为强随机值）
cp .env.example .env && vim .env

# 3. 一键部署（构建镜像 -> 启动 -> Flyway 迁移 -> 健康检查）
chmod +x deploy.sh backup.sh && ./deploy.sh

# 4. 验证：浏览器访问 http://<服务器IP>/，默认 admin/admin123（登录后立即改密）
```

`deploy.sh` 内置防呆：`.env` 缺失或仍含 `change-me` 占位密钥时直接中止；部署完成自动输出容器状态与运维命令。

## 四、日常运维

```bash
# 更新版本（拉新代码后）
./deploy.sh

# 查看日志
docker compose logs -f backend     # 后端
docker compose logs -f frontend    # 网关

# 数据库备份（建议 crontab 每日 03:30）
./backup.sh                        # 默认保留 7 天：30 3 * * * /opt/crm/deploy/backup.sh

# 恢复备份
gunzip -c backups/crm-db-<时间戳>.sql.gz | docker compose exec -T postgres psql -U crm -d crm

# 重启 / 停止
docker compose restart backend
docker compose down                 # 数据保留在 volume，不加 -v
```

## 五、HTTPS 建议

方案 A（推荐）：云厂商负载均衡/CDN 直接挂 443 证书回源 80，免运维。
方案 B：服务器上 certbot 签发，nginx.conf 增加 443 server 块并挂载证书目录（`/etc/letsencrypt`），80 跳转 443。

## 六、常见问题

| 现象 | 排查 |
|---|---|
| deploy.sh 报后端未就绪 | `docker compose logs backend`：多为 `.env` 密钥错误或 PG 未起 |
| 页面 502 | backend 未起或重启中，看 backend 日志 |
| WS 连不上 | 确认访问入口是 80/443 经 nginx（同源）；nginx 已配置 `/ws/` upgrade 透传 |
| Flyway 迁移失败 | 多为库内残留对象；备份后 `docker compose down -v` 重置（会清数据，慎用） |
| 忘记 admin 密码 | 临时将库中该用户密码字段重置为已知 bcrypt 串后登录改密 |
