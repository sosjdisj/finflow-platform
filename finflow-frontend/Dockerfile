# FinFlow 前端镜像（S41）：Node 构建静态资源 → Nginx 托管 + API 反代
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/node:24-alpine AS build

WORKDIR /app

# 先装依赖，利用镜像层缓存
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 运行时 ----------
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
