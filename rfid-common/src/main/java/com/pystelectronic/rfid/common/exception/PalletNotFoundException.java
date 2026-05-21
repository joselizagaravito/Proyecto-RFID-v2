package com.pystelectronic.rfid.common.exception;

import org.springframework.http.HttpStatus;

public class PalletNotFoundException extends RfidBusinessException {
    public PalletNotFoundException(String palletId) {
        super("PALLET_NOT_FOUND",
              "Pallet no encontrado: " + palletId,
              HttpStatus.NOT_FOUND);
    }
}
