-- ================================================================
-- V7: Índice en lpn.epc para validación RFID en tiempo real
-- ================================================================
-- El validation-service consulta lpn.epc en cada lectura de tag.
-- Sin índice, un traslado con 1000 LPNs hace un full scan en cada EPC.
-- Con índice btree: lookup O(log n) → < 1ms en tabla con millones de rows.
--
-- Sprint 5 — fecha: 2026-05
-- ================================================================

-- Índice principal: búsqueda directa por EPC
CREATE INDEX IF NOT EXISTS idx_lpn_epc
    ON lpn (epc);

-- Índice compuesto: EPC + estado del traslado (cubre el WHERE completo del query)
-- Permite index-only scan sin tocar la tabla lpn ni hacer join extra.
-- Necesita join con pallet y transfer; el índice cubre el epc del lado lpn.
CREATE INDEX IF NOT EXISTS idx_lpn_epc_pallet
    ON lpn (epc, pallet_id);

-- Índice en transfer.status para el filtro IN ('PREPARED','DISPATCHED','IN_TRANSIT')
CREATE INDEX IF NOT EXISTS idx_transfer_status
    ON transfer (status)
    WHERE status IN ('PREPARED', 'DISPATCHED', 'IN_TRANSIT');

COMMENT ON INDEX idx_lpn_epc IS
    'Sprint 5: lookup de EPC desde validation-service — critico para latencia < 500ms';
