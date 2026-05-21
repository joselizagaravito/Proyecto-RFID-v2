package com.pystelectronic.rfid.common.exception;

import org.springframework.http.HttpStatus;

public class TransferNotFoundException extends RfidBusinessException {
    public TransferNotFoundException(String transferId) {
        super("TRANSFER_NOT_FOUND",
              "Traslado no encontrado: " + transferId,
              HttpStatus.NOT_FOUND);
    }
}
