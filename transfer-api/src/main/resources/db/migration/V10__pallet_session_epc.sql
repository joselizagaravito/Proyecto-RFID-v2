-- ═══════════════════════════════════════════════════════════════
--  V10 — Sesión de Pallet + EPC de 36 caracteres alfanuméricos
--  Sprint 9 · Pystelectronic · Ing. José Hernán Liza Garavito
--
--  Cambios:
--   1. pallet.epc  → columna nueva (36 chars, UNIQUE) = tag físico del pallet
--   2. lpn.epc     → ampliar de 24 a 36 chars y CHECK alfanumérico
--   3. pallet_tag.epc → ampliar a 36 chars (acepta los nuevos tags)
--   4. pallet_lpn_sequence → secuencia diaria para códigos PL/LPN
--      (separada de transfer_sequence para no mezclar correlativos)
-- ═══════════════════════════════════════════════════════════════

-- ── 1. Columna EPC en pallet (tag físico del pallet) ──────────
ALTER TABLE pallet ADD COLUMN IF NOT EXISTS epc VARCHAR(36);

-- Índice único parcial: permite múltiples NULL pero EPC único cuando existe
CREATE UNIQUE INDEX IF NOT EXISTS idx_pallet_epc_unique
    ON pallet (epc) WHERE epc IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pallet_epc ON pallet (epc);

-- CHECK alfanumérico de 36 chars (solo si epc no es null)
ALTER TABLE pallet DROP CONSTRAINT IF EXISTS chk_pallet_epc_format;
ALTER TABLE pallet ADD CONSTRAINT chk_pallet_epc_format
    CHECK (epc IS NULL OR epc ~ '^[0-9A-Za-z]{1,36}$');

-- ── 2. Ampliar lpn.epc a 36 chars + CHECK alfanumérico ────────
-- Quitar el CHECK viejo (^[0-9A-F]{24}$)
ALTER TABLE lpn DROP CONSTRAINT IF EXISTS chk_epc_format;

-- Ampliar la columna
ALTER TABLE lpn ALTER COLUMN epc TYPE VARCHAR(36);

-- Nuevo CHECK alfanumérico, longitud flexible hasta 36
ALTER TABLE lpn ADD CONSTRAINT chk_epc_format
    CHECK (epc IS NULL OR epc ~ '^[0-9A-Za-z]{1,36}$');

-- ── 3. Ampliar pallet_tag.epc a 36 chars ──────────────────────
-- (la tabla pallet_tag mapea qué EPCs son pallets)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pallet_tag' AND column_name = 'epc'
    ) THEN
        ALTER TABLE pallet_tag ALTER COLUMN epc TYPE VARCHAR(36);
    END IF;
END $$;

-- ── 4. Secuencia diaria para pallets y LPNs ───────────────────
-- Estructura idéntica a transfer_sequence pero con prefijo de tipo
-- seq_key = '{tipo}-{YYMMDD}'  ej: 'PL-260617', 'LPN-260617'
CREATE TABLE IF NOT EXISTS pallet_lpn_sequence (
    seq_key   VARCHAR(16) PRIMARY KEY,
    next_val  BIGINT NOT NULL DEFAULT 1
);

COMMENT ON TABLE pallet_lpn_sequence IS
    'Secuencia diaria para generar pallet_code (PL+YYMMDD+6díg) y lpn_code (YYMMDD+8díg)';

-- ── 5. Sesión de pallet por portal ────────────────────────────
-- Estado de negocio: qué pallet está "activo" en cada portal para
-- asociarle los LPN que se lean a continuación.
-- Una fila por portal (portal_id es PK → solo un pallet activo a la vez).
CREATE TABLE IF NOT EXISTS portal_session (
    portal_id         VARCHAR(64)  PRIMARY KEY,
    active_pallet_id  UUID         REFERENCES pallet(id) ON DELETE SET NULL,
    transfer_id       UUID         REFERENCES transfer(id) ON DELETE CASCADE,
    lpn_count         INTEGER      NOT NULL DEFAULT 0,
    opened_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_read_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_portal_session_pallet
    ON portal_session (active_pallet_id);
CREATE INDEX IF NOT EXISTS idx_portal_session_last_read
    ON portal_session (last_read_at);

COMMENT ON TABLE portal_session IS
    'Pallet activo por portal RFID. Los LPN leídos se asocian al active_pallet_id. Expira por inactividad (last_read_at).';
