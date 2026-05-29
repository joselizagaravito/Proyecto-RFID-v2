-- ═══════════════════════════════════════════════════════════════════
-- Migración V8 — Tabla pallet_tag
-- Sprint 8 · Pystelectronic · Ing. José Hernán Liza Garavito
-- Mayo 2026
--
-- APLICACIÓN MANUAL (igual que V7 y V8):
--
--   1. Copiar al servidor:
--      scp V8__create_pallet_tag.sql joseliza@38.253.180.55:~/app/rfid-backend/
--
--   2. Aplicar SQL en PostgreSQL:
--      docker exec -i rfid-postgres psql -U rfid_app -d rfid_transfers \
--        < ~/app/rfid-backend/V8__create_pallet_tag.sql
--
--   3. Marcar en Flyway:
--      docker exec rfid-postgres psql -U rfid_app -d rfid_transfers -c "
--        INSERT INTO flyway_schema_history
--          (installed_rank, version, description, type, script,
--           checksum, installed_by, installed_on, execution_time, success)
--        VALUES (
--          (SELECT COALESCE(MAX(installed_rank),0)+1 FROM flyway_schema_history),
--          '8', 'create pallet tag', 'SQL', 'V8__create_pallet_tag.sql',
--          0, 'rfid_app', now(), 50, true
--        );"
--
--   4. Verificar:
--      docker exec rfid-postgres psql -U rfid_app -d rfid_transfers \
--        -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS pallet_tag (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    epc          VARCHAR(50)  NOT NULL,
    tid          VARCHAR(50)  NULL,
    descripcion  VARCHAR(100) NULL,
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(40)  NOT NULL DEFAULT 'system',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pallet_tag_epc
    ON pallet_tag (epc);

CREATE INDEX IF NOT EXISTS idx_pallet_tag_activo
    ON pallet_tag (activo) WHERE activo = TRUE;

COMMENT ON TABLE  pallet_tag             IS 'Tags RFID físicos que identifican pallets (fuente: portal web Sprint 8)';
COMMENT ON COLUMN pallet_tag.epc         IS 'Código EPC del chip RFID sin guiones, mayúsculas. Ej: E200001D880C018010209CBA';
COMMENT ON COLUMN pallet_tag.tid         IS 'Tag Identifier del chip — puede contener la PalletTidKeyword del app.config';
COMMENT ON COLUMN pallet_tag.descripcion IS 'Descripción libre del pallet o del tag físico';
COMMENT ON COLUMN pallet_tag.activo      IS 'FALSE = desactivado (soft-delete, no se elimina para mantener auditoría)';
COMMENT ON COLUMN pallet_tag.created_by  IS 'Usuario del portal web que registró el tag';
