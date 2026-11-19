# Spring Boot 백엔드 (Gradle, Java 17)
# 빌드: docker compose --profile spring up -d --build backend-spring

# Stage 1: build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성 레이어 캐시
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# Stage 2: runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# SQLite DB 는 볼륨에 둔다 (컨테이너를 다시 만들어도 데이터 유지)
RUN mkdir -p /data
ENV DATABASE_URL=jdbc:sqlite:/data/rlead.db
VOLUME /data

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/api/cards/health > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
