package com.pystelectronic.rfid.common.exception;

import org.springframework.http.HttpStatus;

public class DuplicateLpnException extends RfidBusinessException {
    public DuplicateLpnException(String lpnCode) {
        super("DUPLICATE_LPN",
              "El código LPN ya existe en el sistema: " + lpnCode,
              HttpStatus.CONFLICT);
    }
}
