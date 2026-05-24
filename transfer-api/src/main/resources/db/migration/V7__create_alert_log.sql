-- V7__create_alert_log.sql
-- Tabla de registro de alertas enviadas al WMS/ERP
-- Permite auditar cada anomalía: tipo, EPC implicado, resultado del webhook y reintentos.

CREATE TABLE IF NOT EXISTS alert_log (
    id                UUID         PRIMARY KEY,
    transfer_id       UUID         REFERENCES transfer(id) ON DELETE SET NULL,
    alert_type        VARCHAR(30)  NOT NULL,
    lpn_code          VARCHAR(14),
    epc               CHAR(24),
    sku_code          VARCHAR(10),
    expected_quantity INTEGER,
    received_quantity INTEGER,
    device_id         VARCHAR(40),
    portal_location   VARCHAR(100),
    webhook_url       VARCHAR(500) NOT NULL,
    webhook_status    SMALLINT,
    retry_count       SMALLINT     NOT NULL DEFAULT 0,
    delivered_at      TIMESTAMPTZ,
    correlation_id    UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Índice para búsquedas de auditoría por EPC
CREATE INDEX IF NOT EXISTS idx_alert_log_epc
    ON alert_log (epc)
    WHERE epc IS NOT NULL;

-- Índice para filtrar por traslado
CREATE INDEX IF NOT EXISTS idx_alert_log_transfer_id
    ON alert_log (transfer_id)
    WHERE transfer_id IS NOT NULL;

-- Índice para buscar por tipo de alerta y fecha (reportes)
CREATE INDEX IF NOT EXISTS idx_alert_log_type_created
    ON alert_log (alert_type, created_at DESC);

-- Índice para correlationId (trazabilidad)
CREATE INDEX IF NOT EXISTS idx_alert_log_correlation_id
    ON alert_log (correlation_id)
    WHERE correlation_id IS NOT NULL;

COMMENT ON TABLE  alert_log                  IS 'Registro de alertas de anomalías RFID enviadas al WMS/ERP';
COMMENT ON COLUMN alert_log.alert_type       IS 'PALLET_INCOMPLETE | EXTRA_LPN | UNREGISTERED_EPC | MISSING_LPN | QUANTITY_MISMATCH';
COMMENT ON COLUMN alert_log.webhook_status   IS 'Código HTTP de respuesta del WMS/ERP (200, 202, 500, etc.)';
COMMENT ON COLUMN alert_log.retry_count      IS 'Número de intentos realizados (máximo 3 con backoff 1s/3s/9s)';
COMMENT ON COLUMN alert_log.delivered_at     IS 'Fecha/hora de la primera entrega exitosa al WMS/ERP';
COMMENT ON COLUMN alert_log.correlation_id   IS 'ID de trazabilidad del evento Kafka que originó la alerta';

-- Fix: asegurar que transfer_id y device_id son nullable (alertas sin traslado activo)
ALTER TABLE alert_log ALTER COLUMN transfer_id DROP NOT NULL;
ALTER TABLE alert_log ALTER COLUMN device_id   DROP NOT NULL;