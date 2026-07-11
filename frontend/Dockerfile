# 前端静态站点：无需 node 构建，直接用 nginx 托管
FROM nginx:1.18-alpine

# 删除官方默认配置，改用我们的配置
RUN rm -f /etc/nginx/conf.d/default.conf

# 复制前端文件到 nginx 默认站点根目录
COPY . /usr/share/nginx/html

# 复制自定义 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
