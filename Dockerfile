# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS source
WORKDIR /workspace
COPY pom.xml ./
COPY config ./config
COPY src ./src

# package runs quality and unit tests, without Docker access or integration tests.
FROM source AS build
RUN --mount=type=cache,target=/root/.m2 mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:21.0.8_9-jre-jammy AS runtime
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/monitor.jar /app/monitor.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/monitor.jar"]
