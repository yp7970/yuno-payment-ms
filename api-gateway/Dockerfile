FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent POM and all module sources for multi-module build
COPY pom.xml .
COPY commons/pom.xml commons/pom.xml
COPY commons/src commons/src
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY api-gateway/src api-gateway/src
# Include other module POMs so Maven resolves the reactor properly
COPY payment-service/pom.xml payment-service/pom.xml
COPY provider-service/pom.xml provider-service/pom.xml
COPY idempotency-service/pom.xml idempotency-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

# Build only api-gateway (and commons it depends on) — skip tests for image build
RUN mvn clean package -DskipTests -pl api-gateway -am -q

# ── Runtime image ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S yunogroup && adduser -S yunouser -G yunogroup
USER yunouser

COPY --from=build /app/api-gateway/target/api-gateway-2.0.0.jar app.jar

EXPOSE 8080

# Container-aware JVM flags: auto-detect cgroup memory limits, use 75% for heap
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
