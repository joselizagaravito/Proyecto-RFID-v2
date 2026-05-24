# ── Build stage ───────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# POM raíz y TODOS los módulos (Maven los necesita para resolver el reactor)
COPY pom.xml .
COPY rfid-common/pom.xml rfid-common/
COPY transfer-api/pom.xml transfer-api/
COPY read-tag-service/pom.xml read-tag-service/
COPY validation-service/pom.xml validation-service/
COPY audit-service/pom.xml audit-service/

RUN mvn dependency:go-offline -pl audit-service -am -q

COPY rfid-common/src rfid-common/src
COPY audit-service/src audit-service/src

RUN mvn clean package -pl audit-service -am -DskipTests -q

# ── Runtime stage ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S rfid && adduser -S rfid -G rfid
USER rfid

COPY --from=builder /build/audit-service/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
