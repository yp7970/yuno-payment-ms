FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY commons/pom.xml commons/pom.xml
COPY commons/src commons/src
COPY idempotency-service/pom.xml idempotency-service/pom.xml
COPY idempotency-service/src idempotency-service/src
# Include other module POMs for reactor resolution
COPY payment-service/pom.xml payment-service/pom.xml
COPY provider-service/pom.xml provider-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

RUN mvn clean package -DskipTests -pl idempotency-service -am -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S yunogroup && adduser -S yunouser -G yunogroup
USER yunouser

COPY --from=build /app/idempotency-service/target/idempotency-service-2.0.0.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
