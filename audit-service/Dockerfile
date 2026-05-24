# ─────────────────────────────────────────────────────────────────
# audit-service/Dockerfile
# Igual estructura que transfer-api — build multistage con Java 21
# ─────────────────────────────────────────────────────────────────

# ── Build stage ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# POM raíz y módulos necesarios para resolver dependencias
COPY pom.xml .
COPY rfid-common/pom.xml rfid-common/
COPY audit-service/pom.xml audit-service/

# Pre-descargar dependencias (capa cacheada entre builds)
RUN mvn -B dependency:go-offline -pl audit-service -am -q 2>/dev/null || true

# Código fuente
COPY rfid-common/src rfid-common/src
COPY audit-service/src audit-service/src

# Compilar solo este módulo y rfid-common
RUN mvn -B clean package -pl audit-service -am -DskipTests -q

# ── Runtime stage ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario no-root
RUN addgroup -S rfid && adduser -S rfid -G rfid
USER rfid

COPY --from=build /workspace/audit-service/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
