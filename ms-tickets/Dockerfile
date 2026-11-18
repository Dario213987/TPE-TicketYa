# syntax=docker/dockerfile:1

# Build: compila el módulo y las dependencias del reactor (libs/contracts).
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -pl ms-tickets -am package -DskipTests

# Runtime: solo JRE 25 + jar, usuario no root y healthcheck.
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/ms-tickets/target/*.jar /app/app.jar
RUN mkdir -p /app/logs && chown -R app:app /app
USER app
ENV SERVER_PORT=8080 \
    TICKETYA_LOGGING_DIR=/app/logs
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
