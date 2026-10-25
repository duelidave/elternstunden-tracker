# syntax=docker/dockerfile:1
# Application image (M0). Build context is the repository root, because the backend generates its API
# code from api/openapi.yaml. Hardening for production follows in M7 (digest-pinned base images,
# read-only root filesystem).

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts gradle.properties ./
COPY gradle gradle
COPY api api
COPY backend/build.gradle.kts backend/
COPY backend/src/main backend/src/main
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :backend:bootJar

FROM eclipse-temurin:25-jre-alpine
# Security fixes of Alpine that the base image does not ship yet (found by the Trivy image scan)
RUN apk upgrade --no-cache && addgroup -S app && adduser -S -G app -H app
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/app.jar /app/app.jar
USER app
# 8080: API, 8081: management (health), internal only
EXPOSE 8080 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
