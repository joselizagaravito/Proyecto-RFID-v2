-- ============================================================
-- V6__add_raw_read_columns.sql
-- Sprint 4: agrega columnas de lecturas crudas de hardware
-- a la tabla read_tag existente (creada en V1).
-- ALTER TABLE no destructivo — preserva datos existentes.
-- ============================================================

ALTER TABLE read_tag
    ADD COLUMN IF NOT EXISTS tag          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS tid          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS inv_times    INTEGER,
    ADD COLUMN IF NOT EXISTS rssi         INTEGER,
    ADD COLUMN IF NOT EXISTS ant_id       INTEGER,
    ADD COLUMN IF NOT EXISTS last_time    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS first_update TIMESTAMP,
    ADD COLUMN IF NOT EXISTS modulo_id    VARCHAR(40),
    ADD COLUMN IF NOT EXISTS modulo_rol   VARCHAR(40),
    ADD COLUMN IF NOT EXISTS color        VARCHAR(30);

-- Defaults para columnas NOT NULL del esquema V1 que
-- el read-tag-service no conoce (hardware no las envía)
ALTER TABLE read_tag
    ALTER COLUMN read_datetime SET DEFAULT NOW(),
    ALTER COLUMN device_id     SET DEFAULT 'UNKNOWN',
    ALTER COLUMN device_type   SET DEFAULT 'RFID_READER',
    ALTER COLUMN user_id       SET DEFAULT 'SYSTEM',
    ALTER COLUMN event_type    SET DEFAULT 'RAW_READ',
    ALTER COLUMN epc           TYPE VARCHAR(96);

-- Indices para columnas de filtrado frecuente
CREATE INDEX IF NOT EXISTS idx_read_tag_modulo_id ON read_tag (modulo_id);
CREATE INDEX IF NOT EXISTS idx_read_tag_last_time ON read_tag (last_time DESC);

COMMENT ON COLUMN read_tag.ant_id       IS 'ID de antena lectora (1-4 en portales de 4 antenas)';
COMMENT ON COLUMN read_tag.inv_times    IS 'Numero de sesiones de inventario en que fue detectado';
COMMENT ON COLUMN read_tag.rssi         IS 'Potencia de senal en dBm (-80 a -20 tipico)';
COMMENT ON COLUMN read_tag.modulo_id    IS 'Identificador del modulo lector (GATE-OUT-01, etc.)';
COMMENT ON COLUMN read_tag.modulo_rol   IS 'Rol del modulo: puerta1, puerta2, handheld';
COMMENT ON COLUMN read_tag.last_time    IS 'Timestamp de la ultima lectura del EPC en la sesion';
COMMENT ON COLUMN read_tag.first_update IS 'Timestamp de la primera lectura del EPC en la sesion';
COMMENT ON COLUMN read_tag.color        IS 'Color para clasificacion visual en dashboard';