FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY backend/gradlew backend/gradlew
COPY backend/gradle backend/gradle
COPY backend/build.gradle backend/settings.gradle ./backend/
RUN chmod +x backend/gradlew

WORKDIR /workspace/backend
RUN ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

COPY backend/src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

