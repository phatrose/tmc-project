# PHỤ TRÁCH: Task 8. Build context là tmc-system, không chứa secrets.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY shared-contracts ./shared-contracts
COPY central-backend ./central-backend
COPY edge-gate-agent/pom.xml ./edge-gate-agent/pom.xml
RUN mvn -B -ntp -pl central-backend -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/central-backend/target/central-backend-*.jar app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
