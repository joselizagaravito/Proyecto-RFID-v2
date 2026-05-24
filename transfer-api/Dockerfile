# ─── Etapa 1: Build con Maven ───────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copiar POMs primero para aprovechar caché de dependencias
COPY pom.xml .
COPY rfid-common/pom.xml rfid-common/
COPY transfer-api/pom.xml transfer-api/
COPY read-tag-service/pom.xml read-tag-service/
COPY validation-service/pom.xml validation-service/
COPY audit-service/pom.xml audit-service/
COPY audit-service/pom.xml audit-service/

# Descargar dependencias (esta capa se cachea mientras no cambie el POM)
RUN mvn dependency:go-offline -pl transfer-api -am -q

# Copiar código fuente
COPY rfid-common/src rfid-common/src
COPY transfer-api/src transfer-api/src

# Compilar y empaquetar (skip tests en imagen Docker; se corren en CI)
RUN mvn package -pl transfer-api -am -DskipTests -q

# ─── Etapa 2: Imagen de runtime (JRE mínimo) ────────────────────
FROM eclipse-temurin:21-jre-alpine

# Usuario sin privilegios
RUN addgroup -S rfid && adduser -S rfid -G rfid
USER rfid

WORKDIR /app

# Copiar JAR desde etapa de build
COPY --from=builder /build/transfer-api/target/transfer-api-*.jar app.jar

# Variables de entorno con valores por defecto seguros
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8080

# Usar Virtual Threads de Java 21 explícitamente
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
