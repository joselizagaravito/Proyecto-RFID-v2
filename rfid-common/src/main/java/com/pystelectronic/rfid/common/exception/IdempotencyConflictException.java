package com.pystelectronic.rfid.common.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends RfidBusinessException {
    public IdempotencyConflictException(String key) {
        super("IDEMPOTENCY_CONFLICT",
              "La clave de idempotencia ya fue procesada: " + key,
              HttpStatus.CONFLICT);
    }
}
