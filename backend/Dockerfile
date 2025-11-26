# Backend 배포용 Dockerfile
# 멀티 스테이지 빌드로 최적화된 이미지 생성

# Stage 1: Build
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Gradle 파일 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 의존성 다운로드 (캐시 활용)
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# 소스코드 복사 및 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 보안: non-root 사용자 생성
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

EXPOSE 8080

# JVM 최적화 옵션
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
