FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
COPY rfid-common/pom.xml rfid-common/
COPY transfer-api/pom.xml transfer-api/
COPY read-tag-service/pom.xml read-tag-service/
COPY validation-service/pom.xml validation-service/
COPY audit-service/pom.xml audit-service/
COPY audit-service/pom.xml audit-service/

RUN mvn dependency:go-offline -pl read-tag-service -am -q

COPY rfid-common/src rfid-common/src
COPY read-tag-service/src read-tag-service/src

RUN mvn package -pl read-tag-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S rfid && adduser -S rfid -G rfid
USER rfid
WORKDIR /app
COPY --from=builder /build/read-tag-service/target/read-tag-service-*.jar app.jar
ENV SERVER_PORT=8082 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseZGC -XX:+ZGenerational"
EXPOSE 8082
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]