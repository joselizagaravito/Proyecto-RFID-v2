package com.pystelectronic.rfid.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Mensaje consumido desde el topic rfid.raw-reads.
 * Producido por: read-tag-service (Sprint 4).
 *
 * Contrato establecido en Sprint 2 — NO modificar campos existentes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawReadMessage(
        String correlationId,
        String portalId,
        String epc,
        Integer rssi,
        Instant readAt,
        String readTagId   // UUID de la entidad ReadTag persistida en Sprint 4
) {}
