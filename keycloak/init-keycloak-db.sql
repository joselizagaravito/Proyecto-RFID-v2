-- ═══════════════════════════════════════════════════════════
--  init-keycloak-db.sql
--  Crea la base de datos keycloak_db si no existe.
--  Se ejecuta automáticamente al iniciar rfid-postgres.
--  El usuario rfid_app (definido en POSTGRES_USER) ya existe.
-- ═══════════════════════════════════════════════════════════

SELECT 'CREATE DATABASE keycloak_db OWNER rfid_app'
WHERE NOT EXISTS (
    SELECT FROM pg_database WHERE datname = 'keycloak_db'
)\gexec

GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO rfid_app;
