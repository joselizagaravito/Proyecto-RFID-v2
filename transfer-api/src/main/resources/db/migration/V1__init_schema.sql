-- ============================================================
-- V1__init_schema.sql
-- RFID Transfers — Esquema inicial completo
-- Pystelectronic · Mayo 2026
-- ============================================================

-- Extensión para UUIDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── Tipos ENUM PostgreSQL ─────────────────────────────────────

CREATE TYPE transfer_status AS ENUM (
    'DRAFT','PREPARED','DISPATCHED','IN_TRANSIT',
    'PARTIALLY_RECEIVED','RECEIVED','CLOSED','CANCELLED','FLAGGED'
);

CREATE TYPE pallet_status AS ENUM (
    'CREATED','BUILDING','PREPARED','VALIDATED','DISPATCHED',
    'IN_TRANSIT','RECEIVED','PARTIALLY_RECEIVED','FLAGGED','CANCELLED'
);

CREATE TYPE lpn_status AS ENUM (
    'CREATED','REGISTERED','VALIDATED','DISPATCHED',
    'IN_TRANSIT','RECEIVED','MISSING','FLAGGED','UNEXPECTED'
);

CREATE TYPE transfer_priority AS ENUM ('LOW','NORMAL','MEDIUM','HIGH','URGENT');

CREATE TYPE content_type AS ENUM ('LPN','LOOSE_ITEM');

CREATE TYPE device_type AS ENUM ('PORTAL','HANDHELD','TUNNEL');

CREATE TYPE alert_type AS ENUM (
    'PALLET_INCOMPLETE','EXTRA_LPN','UNREGISTERED_EPC',
    'MISSING_LPN','QUANTITY_MISMATCH'
);

CREATE TYPE incident_type AS ENUM (
    'MISSING','OVERAGE','UNEXPECTED_LPN','UNEXPECTED_ITEM','QUANTITY_MISMATCH'
);

CREATE TYPE rfid_event_type AS ENUM (
    'WRITE','READ_OUTBOUND','READ_INBOUND','VERIFY','REJECT'
);

-- ============================================================
-- 1. transfer
-- ============================================================
CREATE TABLE transfer (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_code       VARCHAR(25)  NOT NULL UNIQUE,
    origin_code         VARCHAR(10)  NOT NULL,
    destination_code    VARCHAR(10)  NOT NULL,
    status              VARCHAR(25) NOT NULL DEFAULT 'DRAFT',
    priority            VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    carrier_id          VARCHAR(36),
    scheduled_date      TIMESTAMPTZ  NOT NULL,
    dispatched_at       TIMESTAMPTZ,
    received_at         TIMESTAMPTZ,
    vehicle_plate       VARCHAR(20),
    shipping_note       VARCHAR(30),
    remarks             VARCHAR(500),
    total_pallets       INTEGER NOT NULL DEFAULT 0,
    total_lpns          INTEGER NOT NULL DEFAULT 0,
    total_loose_items   INTEGER NOT NULL DEFAULT 0,
    total_units         INTEGER NOT NULL DEFAULT 0,
    idempotency_key     VARCHAR(100) UNIQUE,
    created_by          VARCHAR(40)  NOT NULL,
    updated_by          VARCHAR(40),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_transfer_origin_dest CHECK (origin_code <> destination_code),
    CONSTRAINT chk_transfer_code_format CHECK (transfer_code ~ '^OT-[0-9]{8}-[0-9]{6}$')
);

CREATE INDEX idx_transfer_status          ON transfer (status);
CREATE INDEX idx_transfer_origin          ON transfer (origin_code);
CREATE INDEX idx_transfer_destination     ON transfer (destination_code);
CREATE INDEX idx_transfer_scheduled_date  ON transfer (scheduled_date);
CREATE INDEX idx_transfer_created_at      ON transfer (created_at DESC);
CREATE INDEX idx_transfer_code            ON transfer (transfer_code);

-- ============================================================
-- 2. transfer_sequence (contador para generar transfer_code)
-- ============================================================
CREATE TABLE transfer_sequence (
    date_key    VARCHAR(8) PRIMARY KEY,          -- YYYYMMDD
    next_val    BIGINT  NOT NULL DEFAULT 1
);

-- ============================================================
-- 3. pallet
-- ============================================================
CREATE TABLE pallet (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pallet_code     VARCHAR(14)  NOT NULL UNIQUE,
    transfer_id     UUID         NOT NULL REFERENCES transfer(id) ON DELETE CASCADE,
    status          VARCHAR(25) NOT NULL DEFAULT 'CREATED',
    gross_weight    NUMERIC(10,2),
    height_cm       NUMERIC(10,2),
    width_cm        NUMERIC(10,2),
    length_cm       NUMERIC(10,2),
    total_lpns      INTEGER NOT NULL DEFAULT 0,
    total_loose_items INTEGER NOT NULL DEFAULT 0,
    total_units     INTEGER NOT NULL DEFAULT 0,
    remarks         VARCHAR(250),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pallet_code_format CHECK (pallet_code ~ '^PL[0-9]{12}$')
);

CREATE INDEX idx_pallet_transfer_id ON pallet (transfer_id);
CREATE INDEX idx_pallet_code        ON pallet (pallet_code);
CREATE INDEX idx_pallet_status      ON pallet (status);

-- ============================================================
-- 4. lpn (License Plate Number)
-- ============================================================
CREATE TABLE lpn (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lpn_code        VARCHAR(14)  NOT NULL UNIQUE,
    epc             VARCHAR(24)     UNIQUE,
    pallet_id       UUID         NOT NULL REFERENCES pallet(id) ON DELETE CASCADE,
    transfer_id     UUID         NOT NULL REFERENCES transfer(id),
    status          VARCHAR(25) NOT NULL DEFAULT 'CREATED',
    is_kit          BOOLEAN      NOT NULL DEFAULT FALSE,
    pieces_inside   INTEGER      NOT NULL DEFAULT 0,
    total_units     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_lpn_code_format CHECK (lpn_code ~ '^[0-9]{14}$'),
    CONSTRAINT chk_epc_format      CHECK (epc ~ '^[0-9A-F]{24}$')
);

CREATE INDEX idx_lpn_pallet_id   ON lpn (pallet_id);
CREATE INDEX idx_lpn_transfer_id ON lpn (transfer_id);
CREATE INDEX idx_lpn_code        ON lpn (lpn_code);
CREATE INDEX idx_lpn_epc         ON lpn (epc);
CREATE INDEX idx_lpn_status      ON lpn (status);

-- ============================================================
-- 5. lpn_sku (relación LPN → SKUs, soporte multi-SKU por LPN)
-- ============================================================
CREATE TABLE lpn_sku (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    lpn_id          UUID         NOT NULL REFERENCES lpn(id) ON DELETE CASCADE,
    sku_code        VARCHAR(10)  NOT NULL,
    sku_description VARCHAR(100) NOT NULL,
    unit_quantity   INTEGER      NOT NULL CHECK (unit_quantity > 0),

    CONSTRAINT chk_sku_code_format CHECK (sku_code ~ '^[0-9]{4,10}$')
);

CREATE INDEX idx_lpn_sku_lpn_id   ON lpn_sku (lpn_id);
CREATE INDEX idx_lpn_sku_sku_code ON lpn_sku (sku_code);

-- ============================================================
-- 6. loose_item (ítems sueltos sin LPN)
-- ============================================================
CREATE TABLE loose_item (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pallet_id       UUID         NOT NULL REFERENCES pallet(id) ON DELETE CASCADE,
    transfer_id     UUID         NOT NULL REFERENCES transfer(id),
    sku_code        VARCHAR(10)  NOT NULL,
    sku_description VARCHAR(100) NOT NULL,
    unit_quantity   INTEGER      NOT NULL CHECK (unit_quantity > 0),
    status          VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_loose_item_sku CHECK (sku_code ~ '^[0-9]{4,10}$')
);

CREATE INDEX idx_loose_item_pallet_id   ON loose_item (pallet_id);
CREATE INDEX idx_loose_item_transfer_id ON loose_item (transfer_id);

-- ============================================================
-- 7. read_tag (lecturas RFID crudas y validadas)
-- ============================================================
CREATE TABLE read_tag (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_id     UUID         REFERENCES transfer(id),
    lpn_id          UUID         REFERENCES lpn(id),
    lpn_code        VARCHAR(14),
    epc             VARCHAR(24),
    device_id       VARCHAR(40)  NOT NULL,
    device_type     VARCHAR(20) NOT NULL,
    user_id         VARCHAR(40)  NOT NULL,
    portal_location VARCHAR(100),
    event_type      VARCHAR(20) NOT NULL,
    result          VARCHAR(20)  NOT NULL DEFAULT 'OK',
    read_datetime   TIMESTAMPTZ  NOT NULL,
    correlation_id  UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_read_tag_transfer_id  ON read_tag (transfer_id);
CREATE INDEX idx_read_tag_lpn_code     ON read_tag (lpn_code);
CREATE INDEX idx_read_tag_epc          ON read_tag (epc);
CREATE INDEX idx_read_tag_device_id    ON read_tag (device_id);
CREATE INDEX idx_read_tag_read_datetime ON read_tag (read_datetime DESC);

-- ============================================================
-- 8. receipt (recepciones registradas en destino)
-- ============================================================
CREATE TABLE receipt (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_id             UUID        NOT NULL REFERENCES transfer(id),
    receipt_status          VARCHAR(25) NOT NULL,
    expected_lpns           INTEGER     NOT NULL DEFAULT 0,
    received_lpns           INTEGER     NOT NULL DEFAULT 0,
    expected_loose_items    INTEGER     NOT NULL DEFAULT 0,
    received_loose_items    INTEGER     NOT NULL DEFAULT 0,
    expected_total_units    INTEGER     NOT NULL DEFAULT 0,
    received_total_units    INTEGER     NOT NULL DEFAULT 0,
    user_id                 VARCHAR(40) NOT NULL,
    receipt_datetime        TIMESTAMPTZ NOT NULL,
    remarks                 VARCHAR(500),
    idempotency_key         VARCHAR(100) UNIQUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_receipt_transfer_id ON receipt (transfer_id);

-- ============================================================
-- 9. receipt_incident (incidencias detectadas en la recepción)
-- ============================================================
CREATE TABLE receipt_incident (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    receipt_id      UUID          NOT NULL REFERENCES receipt(id) ON DELETE CASCADE,
    transfer_id     UUID          NOT NULL REFERENCES transfer(id),
    incident_type   VARCHAR(30) NOT NULL,
    lpn_code        VARCHAR(14),
    sku_code        VARCHAR(10),
    sku_description VARCHAR(100),
    expected_qty    INTEGER,
    received_qty    INTEGER,
    unit_quantity   INTEGER,
    details         VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_receipt_incident_receipt_id   ON receipt_incident (receipt_id);
CREATE INDEX idx_receipt_incident_transfer_id  ON receipt_incident (transfer_id);

-- ============================================================
-- 10. alert_log (alertas enviadas al WMS/ERP)
-- ============================================================
CREATE TABLE alert_log (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transfer_id         UUID         NOT NULL REFERENCES transfer(id),
    alert_type          VARCHAR(30) NOT NULL,
    lpn_code            VARCHAR(14),
    epc                 VARCHAR(24),
    sku_code            VARCHAR(10),
    expected_quantity   INTEGER,
    received_quantity   INTEGER,
    device_id           VARCHAR(40)  NOT NULL,
    portal_location     VARCHAR(100),
    webhook_url         VARCHAR(500) NOT NULL,
    webhook_status      SMALLINT,
    retry_count         SMALLINT     NOT NULL DEFAULT 0,
    delivered_at        TIMESTAMPTZ,
    correlation_id      UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_alert_log_transfer_id ON alert_log (transfer_id);
CREATE INDEX idx_alert_log_created_at  ON alert_log (created_at DESC);

-- ============================================================
-- 11. audit_log (trazabilidad de cada petición autenticada)
-- ============================================================
CREATE TABLE audit_log (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    correlation_id      UUID,
    idempotency_key     VARCHAR(100),
    user_id             VARCHAR(40)  NOT NULL,
    user_roles          VARCHAR(100) NOT NULL,
    client_ip           VARCHAR(45)  NOT NULL,
    user_agent          VARCHAR(255),
    http_method         VARCHAR(10)  NOT NULL,
    endpoint_path       VARCHAR(200) NOT NULL,
    path_params         JSONB,
    query_params        JSONB,
    request_summary     JSONB,
    http_status         SMALLINT     NOT NULL,
    error_code          VARCHAR(20),
    duration_ms         INTEGER,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_user_id     ON audit_log (user_id);
CREATE INDEX idx_audit_log_created_at  ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_endpoint    ON audit_log (endpoint_path);
CREATE INDEX idx_audit_log_correlation ON audit_log (correlation_id);

-- ============================================================
-- 12. portal_state (snapshot Redis → PostgreSQL)
-- ============================================================
CREATE TABLE portal_state (
    portal_id           VARCHAR(40)  PRIMARY KEY,
    transfer_id         UUID         REFERENCES transfer(id),
    portal_location     VARCHAR(100) NOT NULL,
    device_type         VARCHAR(20) NOT NULL,
    expected_lpns       INTEGER,
    read_count          INTEGER      NOT NULL DEFAULT 0,
    anomaly_count       INTEGER      NOT NULL DEFAULT 0,
    last_read_at        TIMESTAMPTZ,
    state_snapshot_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ============================================================
-- Trigger: actualizar updated_at automáticamente
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transfer_updated_at
    BEFORE UPDATE ON transfer
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_pallet_updated_at
    BEFORE UPDATE ON pallet
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_lpn_updated_at
    BEFORE UPDATE ON lpn
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

