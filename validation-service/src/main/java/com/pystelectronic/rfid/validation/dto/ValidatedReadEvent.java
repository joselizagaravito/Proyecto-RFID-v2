package com.pystelectronic.rfid.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mensaje publicado en el topic rfid.validated.
 * Consumido por: realtime-service, alert-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidatedReadEvent {

    /** EPC validado. */
    private String epc;

    /** Código LPN asociado al EPC (si existe en la tabla lpn). */
    private String lpnCode;

    /** ID del traslado activo donde se validó el EPC. */
    private String transferId;

    /** Código de negocio del traslado (OT-YYYYMMDD-NNNNNN). */
    private String transferCode;

    /** ID del módulo/portal que leyó el tag. */
    private String deviceId;

    /** Rol del dispositivo: GATE_OUT, GATE_IN, HANDHELD. */
    private String deviceRole;

    /** Resultado: VALID, EXTRA_LPN, UNREGISTERED_EPC. */
    private String validationResult;

    /** Mensaje descriptivo del resultado. */
    private String reason;

    /** Timestamp de la lectura original. */
    private Instant readDateTime;

    /** Timestamp de la validación. */
    private Instant validatedAt;

    /** ID de correlación para trazabilidad. */
    private String correlationId;
}
