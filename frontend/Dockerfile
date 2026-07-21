FROM node:24-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json vite.config.ts ./
COPY src ./src
COPY *.html ./
RUN npm run build

FROM nginx:1.27-alpine

# 删除官方默认配置，改用我们的配置
RUN rm -f /etc/nginx/conf.d/default.conf

# 保留点评旧页面，再用 Vue 构建产物覆盖三个 MeetMate 入口。
COPY *.html favicon.ico /usr/share/nginx/html/
COPY css /usr/share/nginx/html/css
COPY imgs /usr/share/nginx/html/imgs
COPY js /usr/share/nginx/html/js
COPY --from=build /app/dist/ /usr/share/nginx/html/

# 复制自定义 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
