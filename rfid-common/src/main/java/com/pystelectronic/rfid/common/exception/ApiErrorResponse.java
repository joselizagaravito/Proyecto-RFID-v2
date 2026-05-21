package com.pystelectronic.rfid.common.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Envoltorio estándar de error para todos los endpoints REST.
 * Spec §4 — Manejo de Errores.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    /** Código de error interno. Ej: TRANSFER_NOT_FOUND, VAL-002 */
    private String errorCode;

    /** Mensaje legible por humanos */
    private String message;

    /** HTTP status */
    private int status;

    /** ID de correlación de la petición original */
    private UUID correlationId;

    /** Errores de validación campo a campo */
    private List<FieldError> fieldErrors;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime timestamp;

    @Getter
    @Builder
    public static class FieldError {
        private String field;
        private String rejectedValue;
        private String message;
        private String errorCode;
    }
}
