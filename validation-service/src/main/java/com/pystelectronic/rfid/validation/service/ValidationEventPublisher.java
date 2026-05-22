package com.pystelectronic.rfid.validation.service;

import com.pystelectronic.rfid.validation.dto.TransferStateEvent;
import com.pystelectronic.rfid.validation.dto.ValidatedReadEvent;

public interface ValidationEventPublisher {
    void publishValidated(ValidatedReadEvent event);
    void publishTransferEvent(TransferStateEvent event);
    void publishAlert(ValidatedReadEvent alertEvent);
}
