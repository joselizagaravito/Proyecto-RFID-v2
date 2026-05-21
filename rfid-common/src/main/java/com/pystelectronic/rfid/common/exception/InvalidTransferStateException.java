package com.pystelectronic.rfid.common.exception;

import org.springframework.http.HttpStatus;

public class InvalidTransferStateException extends RfidBusinessException {
    public InvalidTransferStateException(String message) {
        super("INVALID_TRANSFER_STATE", message, HttpStatus.CONFLICT);
    }
}
