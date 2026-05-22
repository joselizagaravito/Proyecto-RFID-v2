-- ============================================================
-- V5__create_rfid_event.sql
-- Sprint 3: tabla de eventos RFID para GET /api/v1/rfid-events
-- ============================================================

CREATE TABLE IF NOT EXISTS rfid_event (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    transfer_id    UUID,
    event_type     VARCHAR(20)  NOT NULL,
    epc            VARCHAR(96),
    lpn_code       VARCHAR(20),
    device_id      VARCHAR(40),
    user_id        VARCHAR(40),
    location       VARCHAR(50),
    result         VARCHAR(20),
    error_code     VARCHAR(20),
    timestamp      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    correlation_id VARCHAR(36),

    CONSTRAINT pk_rfid_event PRIMARY KEY (id),
    CONSTRAINT fk_rfid_event_transfer
        FOREIGN KEY (transfer_id)
        REFERENCES transfer(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_rfid_event_transfer  ON rfid_event(transfer_id);
CREATE INDEX IF NOT EXISTS idx_rfid_event_timestamp ON rfid_event(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_rfid_event_lpn       ON rfid_event(lpn_code);
CREATE INDEX IF NOT EXISTS idx_rfid_event_type      ON rfid_event(event_type);

COMMENT ON TABLE rfid_event IS 'Eventos de lectura/validación RFID asociados a traslados';
COMMENT ON COLUMN rfid_event.event_type   IS 'WRITE | READ_OUTBOUND | READ_INBOUND | VERIFY | REJECT';
COMMENT ON COLUMN rfid_event.result       IS 'OK | MISMATCH | INVALID_EPC | INVALID_READ';
COMMENT ON COLUMN rfid_event.correlation_id IS 'X-Correlation-Id del request que generó el evento';