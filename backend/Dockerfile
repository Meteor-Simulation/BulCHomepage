# Backend 배포용 Dockerfile
# 멀티 스테이지 빌드로 최적화된 이미지 생성

# Stage 1: Build
# gradle 공식 이미지 사용 (gradle-wrapper.jar가 .gitignore(*.jar)로 누락되는 문제 해결)
FROM gradle:8.5-jdk17 AS builder

WORKDIR /app

# Gradle 빌드 메모리 제한 (저사양 서버 빌드 대응)
ENV GRADLE_OPTS="-Xms256m -Xmx512m -Dorg.gradle.jvmargs=-Xmx512m"

# Gradle 설정 파일 복사 (의존성 캐시 활용)
COPY build.gradle .
COPY settings.gradle .
RUN gradle dependencies --no-daemon || true

# 소스코드 복사 및 빌드
COPY src src
RUN gradle bootJar --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 보안: non-root 사용자 생성
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

EXPOSE 8080

# JVM 최적화 옵션 (1GB RAM 서버 기준: 128~384MB 힙, 컨테이너 메모리의 50% 사용)
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=50.0", "-Xms128m", "-Xmx384m", "-jar", "app.jar"]
