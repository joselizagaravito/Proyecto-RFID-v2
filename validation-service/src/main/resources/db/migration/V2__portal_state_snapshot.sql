-- Sprint 2: tabla de snapshot para sincronización Redis → PostgreSQL
-- Referencia spec sección 9.1: "portal_state de Redis se sincroniza a PostgreSQL cada 30 segundos"

CREATE TABLE IF NOT EXISTS portal_state_snapshot (
    device_id       VARCHAR(40)  PRIMARY KEY,
    transfer_id     VARCHAR(36),
    transfer_code   VARCHAR(30),
    read_count      INTEGER      NOT NULL DEFAULT 0,
    expected_count  INTEGER      NOT NULL DEFAULT 0,
    last_read_at    BIGINT,                             -- epoch ms de la última lectura
    synced_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE portal_state_snapshot
    IS 'Snapshot periódico del portal_state de Redis. Permite recuperación ante fallo de Redis.';

COMMENT ON COLUMN portal_state_snapshot.last_read_at
    IS 'Epoch en milisegundos de la última lectura RFID registrada en Redis para este portal.';

-- Índice para consulta por traslado (útil para el realtime-service como fallback)
CREATE INDEX IF NOT EXISTS idx_portal_snapshot_transfer
    ON portal_state_snapshot(transfer_id);
