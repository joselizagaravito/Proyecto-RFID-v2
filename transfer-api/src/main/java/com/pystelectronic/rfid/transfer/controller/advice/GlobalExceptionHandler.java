package com.pystelectronic.rfid.transfer.controller.advice;

import com.pystelectronic.rfid.common.exception.ApiErrorResponse;
import com.pystelectronic.rfid.common.exception.RfidBusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manejador global de excepciones.
 * Convierte cualquier excepción al formato estándar ApiErrorResponse.
 * Spec §4.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Errores de validación Bean Validation (@Valid) ────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ApiErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .rejectedValue(fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null)
                        .message(fe.getDefaultMessage())
                        .errorCode("VAL-001")
                        .build())
                .toList();

        ApiErrorResponse body = ApiErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message("Los datos de entrada no son válidos")
                .status(HttpStatus.BAD_REQUEST.value())
                .correlationId(getCorrelationId(request))
                .fieldErrors(fieldErrors)
                .timestamp(OffsetDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── Excepciones de negocio RFID ───────────────────────────────

    @ExceptionHandler(RfidBusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            RfidBusinessException ex,
            HttpServletRequest request) {

        log.warn("Error de negocio [{}]: {}", ex.getErrorCode(), ex.getMessage());

        ApiErrorResponse body = ApiErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(ex.getHttpStatus().value())
                .correlationId(getCorrelationId(request))
                .timestamp(OffsetDateTime.now())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    // ── Acceso denegado (403) ─────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        ApiErrorResponse body = ApiErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message("No tiene permisos para realizar esta operación")
                .status(HttpStatus.FORBIDDEN.value())
                .correlationId(getCorrelationId(request))
                .timestamp(OffsetDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // ── Error interno ─────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Error inesperado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ApiErrorResponse body = ApiErrorResponse.builder()
                .errorCode("INTERNAL_ERROR")
                .message("Ha ocurrido un error interno. Por favor contacte al administrador.")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .correlationId(getCorrelationId(request))
                .timestamp(OffsetDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private UUID getCorrelationId(HttpServletRequest request) {
        String header = request.getHeader("X-Correlation-Id");
        try {
            return header != null ? UUID.fromString(header) : UUID.randomUUID();
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }
}
