FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
# Copy parent POM and commons first for dependency caching
COPY pom.xml .
COPY commons/pom.xml commons/pom.xml
COPY commons/src commons/src
COPY payment-service/pom.xml payment-service/pom.xml
COPY payment-service/src payment-service/src
COPY provider-service/pom.xml provider-service/pom.xml
COPY provider-service/src provider-service/src
COPY idempotency-service/pom.xml idempotency-service/pom.xml
COPY idempotency-service/src idempotency-service/src
COPY notification-service/pom.xml notification-service/pom.xml
COPY notification-service/src notification-service/src
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY api-gateway/src api-gateway/src
RUN mvn clean package -DskipTests -pl payment-service -am -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S yunogroup && adduser -S yunouser -G yunogroup
USER yunouser
COPY --from=build /app/payment-service/target/payment-service-2.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
