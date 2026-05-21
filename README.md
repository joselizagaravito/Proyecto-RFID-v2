# RFID Transfers Backend

Sistema de traslados con tecnología RFID — Pystelectronic

## Stack

| Componente | Tecnología |
|---|---|
| API REST principal | Java 21 + Spring Boot 3.4.x |
| Base de datos | PostgreSQL 16 |
| Caché estado en vivo | Redis 7 |
| Bus de eventos | Apache Kafka 3.7 (KRaft) |
| Autenticación | Keycloak 25 (OAuth 2.0 / JWT) |
| Migraciones | Flyway 10 |
| Tests | JUnit 5 + Testcontainers |

## Inicio rápido

```bash
# 1. Levantar infraestructura
docker compose up -d postgres redis kafka keycloak

# 2. Compilar y ejecutar transfer-api
mvn spring-boot:run -pl transfer-api -Dspring-boot.run.profiles=dev

# 3. Acceder a la documentación
open http://localhost:8080/swagger-ui.html

# 4. Correr tests de integración
mvn test -pl transfer-api
```

## Módulos

- `rfid-common` — DTOs, enums, excepciones compartidos
- `transfer-api` — API REST principal (puerto 8080)
- `read-tag-service` — Sprint 2
- `validation-service` — Sprint 2
- `audit-service` — Sprint 2

## Endpoints principales

| Método | Endpoint | Descripción |
|---|---|---|
| POST | /api/v1/transfers | Crear traslado |
| GET | /api/v1/transfers | Listar (paginado) |
| GET | /api/v1/transfers/{id} | Detalle completo |
| POST | /api/v1/transfers/{id}/pallets | Agregar pallet |
| POST | /api/v1/pallets/{id}/contents | Agregar LPN/ítem suelto |
| POST | /api/v1/transfers/{id}/rfid-validations | Validar RFID |
| POST | /api/v1/transfers/{id}/dispatch | Confirmar despacho |
| POST | /api/v1/transfers/{id}/receipts | Registrar recepción |
| GET | /api/v1/transfers/{id}/reconciliation | Conciliación |
