# 构建阶段
FROM node:20-alpine AS builder

WORKDIR /app

# 通过 corepack 启用 pnpm（Vite 8 要求 Node 20+）
RUN corepack enable && corepack prepare pnpm@9.0.0 --activate

# 复制 package.json 和 lock 文件
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./

# 安装依赖
RUN pnpm install --frozen-lockfile=false

# 复制源代码
COPY . .

# 构建应用
RUN pnpm build

# 生产阶段
FROM nginx:alpine

# 复制 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 从构建阶段复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 暴露端口
EXPOSE 80

# 启动 nginx
CMD ["nginx", "-g", "daemon off;"]