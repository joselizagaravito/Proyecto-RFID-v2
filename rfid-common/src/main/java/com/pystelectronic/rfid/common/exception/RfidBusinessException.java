package com.pystelectronic.rfid.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepción base de negocio con código de error y HTTP status.
 */
@Getter
public class RfidBusinessException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public RfidBusinessException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public RfidBusinessException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
