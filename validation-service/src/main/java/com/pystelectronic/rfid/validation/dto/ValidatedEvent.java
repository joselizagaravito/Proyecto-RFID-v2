package com.pystelectronic.rfid.validation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Payload publicado en el topic rfid.validated.
 *
 * Consumidores:
 *   - realtime-service  → push WebSocket al dashboard (Sprint 5)
 *   - alert-service     → detección de anomalías   (Sprint 6)
 *
 * Contrato de campos:
 *   result      VALID | INVALID | UNKNOWN_EPC | WRONG_TRANSFER
 *   portalId    ID del portal RFID que generó la lectura
 *   epc         Código EPC del tag leído
 *   lpnCode     null si result != VALID
 *   transferId  null si result == UNKNOWN_EPC
 *   skuCode     null si result != VALID
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidatedEvent(
        String correlationId,
        String portalId,
        String epc,
        ValidationResult result,
        String transferId,
        String lpnCode,
        String skuCode,
        String rejectReason,
        Instant readAt,
        Instant validatedAt
) {
    public enum ValidationResult {
        /** EPC existe en la tabla lpn y pertenece al traslado activo del portal */
        VALID,
        /** EPC existe pero pertenece a otro traslado (no al asignado al portal) */
        WRONG_TRANSFER,
        /** EPC no existe en ningún traslado activo */
        UNKNOWN_EPC,
        /** Formato de EPC inválido (filtrado de ruido) */
        INVALID
    }

    /** Factory: lectura válida con todos los datos del LPN */
    public static ValidatedEvent valid(String correlationId, String portalId, String epc,
                                       String transferId, String lpnCode, String skuCode,
                                       Instant readAt) {
        return new ValidatedEvent(correlationId, portalId, epc,
                ValidationResult.VALID, transferId, lpnCode, skuCode,
                null, readAt, Instant.now());
    }

    /** Factory: EPC desconocido */
    public static ValidatedEvent unknownEpc(String correlationId, String portalId,
                                             String epc, Instant readAt) {
        return new ValidatedEvent(correlationId, portalId, epc,
                ValidationResult.UNKNOWN_EPC, null, null, null,
                "EPC no registrado en ningún traslado activo", readAt, Instant.now());
    }

    /** Factory: EPC pertenece a otro traslado */
    public static ValidatedEvent wrongTransfer(String correlationId, String portalId,
                                                String epc, String actualTransferId,
                                                Instant readAt) {
        return new ValidatedEvent(correlationId, portalId, epc,
                ValidationResult.WRONG_TRANSFER, actualTransferId, null, null,
                "EPC pertenece a traslado diferente al asignado al portal", readAt, Instant.now());
    }

    /** Factory: formato inválido */
    public static ValidatedEvent invalid(String correlationId, String portalId,
                                          String epc, String reason, Instant readAt) {
        return new ValidatedEvent(correlationId, portalId, epc,
                ValidationResult.INVALID, null, null, null,
                reason, readAt, Instant.now());
    }
}
