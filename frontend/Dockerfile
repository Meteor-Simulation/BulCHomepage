# Frontend 배포용 Dockerfile
# 멀티 스테이지 빌드: Node.js 빌드 → Nginx 서빙

# Stage 1: Build
FROM node:20-alpine AS builder

WORKDIR /app

# 패키지 파일 복사 및 의존성 설치
COPY package*.json ./
RUN npm ci --only=production=false

# 소스코드 복사 및 빌드
COPY . .
RUN npm run build

# Stage 2: Production with Nginx
FROM nginx:alpine

# Nginx 설정 복사
COPY --from=builder /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 보안: non-root 실행을 위한 권한 설정
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chown -R nginx:nginx /var/cache/nginx && \
    chown -R nginx:nginx /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
