# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline -DskipTests || true
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/hr-management-system-*.jar /build/app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid 1001 app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
# Secrets (JWT_SECRET, DB_PASSWORD, HR_ADMIN_PASSWORD, …) come from the environment /
# compose — nothing sensitive is baked into the image.
EXPOSE 8091
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD curl -fsS http://localhost:${SERVER_PORT:-8091}/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
