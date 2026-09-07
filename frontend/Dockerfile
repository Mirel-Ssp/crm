# =====================================================================
# CRM 前端镜像（多阶段构建：Node 22 + pnpm 11 构建 → nginx 托管静态资源）
# 构建上下文 = frontend/：docker build -t crm-frontend .
# 或在 deploy/ 目录：docker compose build frontend
# =====================================================================

# ---- 构建阶段：npmmirror 加速依赖下载（与后端 Maven 阿里云镜像层对齐） ----
FROM node:22-alpine AS build
WORKDIR /build

RUN corepack enable && corepack prepare pnpm@11.25.0 --activate \
      && pnpm config set registry https://registry.npmmirror.com

# 层缓存：先拷 lockfile 拉依赖，再拷源码
COPY package.json pnpm-lock.yaml ./
# pnpm 11 默认拦截 esbuild postinstall：--ignore-scripts 跳过安装脚本，
# 二进制由 optionalDependencies（含 musl 变体）直接提供
RUN pnpm install --frozen-lockfile --ignore-scripts

COPY . .
RUN pnpm build

# ---- 运行阶段：nginx 托管 dist；反代配置由 compose 挂载 deploy/nginx.conf ----
FROM nginx:1.27-alpine
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
