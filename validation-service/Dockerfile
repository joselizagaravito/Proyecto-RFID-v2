FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
COPY rfid-common/pom.xml rfid-common/
COPY transfer-api/pom.xml transfer-api/
COPY read-tag-service/pom.xml read-tag-service/
COPY validation-service/pom.xml validation-service/
COPY audit-service/pom.xml audit-service/
COPY audit-service/pom.xml audit-service/

RUN mvn dependency:go-offline -pl validation-service -am -q

COPY rfid-common/src rfid-common/src
COPY validation-service/src validation-service/src

RUN mvn package -pl validation-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S rfid && adduser -S rfid -G rfid
USER rfid
WORKDIR /app
COPY --from=builder /build/validation-service/target/validation-service-*.jar app.jar
ENV SERVER_PORT=8083 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseZGC -XX:+ZGenerational"
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]