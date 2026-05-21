package com.pystelectronic.rfid.common.enums;

/**
 * Estado del traslado en su ciclo de vida.
 * Flujo principal: DRAFT → PREPARED → DISPATCHED → IN_TRANSIT → RECEIVED / PARTIALLY_RECEIVED → CLOSED
 */
public enum TransferStatus {
    DRAFT,
    PREPARED,
    DISPATCHED,
    IN_TRANSIT,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CLOSED,
    CANCELLED,
    FLAGGED
}
