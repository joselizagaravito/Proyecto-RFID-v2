-- ═══════════════════════════════════════════════════════════════════
-- Migración V8 — Tabla audit_log
-- Sprint 6: Seguridad y Auditoría
--
-- APLICACIÓN MANUAL (igual que V7):
--
--   1. Aplicar SQL en PostgreSQL:
--      docker exec -i rfid-postgres psql -U rfid_app -d rfid_transfers < V8__create_audit_log.sql
--
--   2. Marcar en Flyway (para que no intente aplicarla de nuevo):
--      docker exec -i rfid-postgres psql -U rfid_app -d rfid_transfers -c "
--        INSERT INTO flyway_schema_history
--          (installed_rank, version, description, type, script,
--           checksum, installed_by, installed_on, execution_time, success)
--        VALUES (
--          (SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history),
--          '8',
--          'create audit log',
--          'SQL',
--          'V8__create_audit_log.sql',
--          0,
--          'rfid_app',
--          now(),
--          100,
--          true
--        );"
--
--   3. Verificar:
--      docker exec -i rfid-postgres psql -U rfid_app -d rfid_transfers -c "\d audit_log"
--
-- Pystelectronic · Ing. José Hernán Liza Garavito · Mayo 2026
-- ═══════════════════════════════════════════════════════════════════

-- Habilitar extensión UUID si no está activa
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Tabla principal de auditoría ──────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Trazabilidad de la petición HTTP
    correlation_id   UUID,
    idempotency_key  VARCHAR(100),

    -- Identidad del usuario autenticado (extraído del JWT)
    user_id          VARCHAR(40)  NOT NULL,
    user_roles       VARCHAR(200) NOT NULL,

    -- Origen de la petición
    client_ip        VARCHAR(45)  NOT NULL,
    user_agent       VARCHAR(255),

    -- Detalles del endpoint invocado
    http_method      VARCHAR(10)  NOT NULL,
    endpoint_path    VARCHAR(200) NOT NULL,
    query_params     JSONB,
    request_summary  JSONB,

    -- Resultado
    http_status      SMALLINT     NOT NULL,
    error_code       VARCHAR(20),
    duration_ms      INTEGER      NOT NULL,

    -- Nivel de detalle registrado
    audit_level      VARCHAR(10)  NOT NULL
                     CHECK (audit_level IN ('FULL', 'STANDARD', 'READ')),

    -- Timestamp con zona horaria
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── Índices para consultas frecuentes ────────────────────────────
CREATE INDEX IF NOT EXISTS idx_audit_user_id
    ON audit_log (user_id);

CREATE INDEX IF NOT EXISTS idx_audit_created_at
    ON audit_log (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_client_ip
    ON audit_log (client_ip);

CREATE INDEX IF NOT EXISTS idx_audit_endpoint
    ON audit_log (endpoint_path);

CREATE INDEX IF NOT EXISTS idx_audit_correlation
    ON audit_log (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_http_status
    ON audit_log (http_status);

CREATE INDEX IF NOT EXISTS idx_audit_level_date
    ON audit_log (audit_level, created_at DESC);

-- ── Tabla de archivo para registros > 12 meses ───────────────────
CREATE TABLE IF NOT EXISTS audit_log_archive (
    LIKE audit_log INCLUDING ALL
);

-- ── Comentarios ───────────────────────────────────────────────────
COMMENT ON TABLE audit_log IS
    'Registro de auditoría — todas las peticiones HTTP autenticadas. Sprint 6. Retención: 12 meses.';
COMMENT ON TABLE audit_log_archive IS
    'Archivo de audit_log — registros con más de 12 meses. Gestión manual o job mensual.';
COMMENT ON COLUMN audit_log.audit_level IS
    'FULL: POSTs críticos + PUT/DELETE | STANDARD: POSTs operacionales | READ: GETs';
COMMENT ON COLUMN audit_log.request_summary IS
    'Resumen sanitizado del body (sin passwords, tokens ni secrets). Solo en FULL y STANDARD.';
