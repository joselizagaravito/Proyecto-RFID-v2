package com.pystelectronic.rfid.validation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Evento de cambio de estado de un traslado.
 * Publicado en transfer.events, consumido por: realtime-service, audit-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferStateEvent {

    /** Tipo de evento: LPN_READ, LPN_VALIDATED, PALLET_COMPLETE, TRANSFER_READ_COMPLETE. */
    private String eventType;

    private String transferId;
    private String transferCode;

    /** LPN que generó el cambio de estado (puede ser null para eventos a nivel traslado). */
    private String lpnCode;

    /** Nuevo estado del LPN o traslado. */
    private String newStatus;

    /** Conteo de LPNs leídos hasta el momento (desde Redis). */
    private Integer readCount;

    /** Total de LPNs esperados según el traslado. */
    private Integer expectedCount;

    private Instant occurredAt;
    private String correlationId;
}
