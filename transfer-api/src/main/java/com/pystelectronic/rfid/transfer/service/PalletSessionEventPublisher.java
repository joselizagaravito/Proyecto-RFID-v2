package com.pystelectronic.rfid.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pystelectronic.rfid.transfer.controller.dto.RfidSessionReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publica eventos de sesión de pallet a Kafka para que el realtime-service
 * los emita por WebSocket al portal en tiempo real.
 *
 * Topic: rfid.session
 * Eventos (campo eventType del payload):
 *   PALLET_OPENED / PALLET_REUSED
 *   LPN_ADDED / LPN_REUSED
 *   LPN_REJECTED
 *
 * Patrón idéntico a AuditLogService (KafkaTemplate<String,String> + ObjectMapper).
 *
 * Sprint 9 · Pystelectronic
 */
@Service
public class PalletSessionEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(PalletSessionEventPublisher.class);
    private static final String TOPIC = "rfid.session";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PalletSessionEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                       ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    public void publishPalletOpened(RfidSessionReadResponse resp) {
        publish(resp);
    }

    public void publishLpnAdded(RfidSessionReadResponse resp) {
        publish(resp);
    }

    public void publishLpnRejected(RfidSessionReadResponse resp) {
        publish(resp);
    }

    private void publish(RfidSessionReadResponse resp) {
        try {
            String payload = objectMapper.writeValueAsString(resp);
            String key = "SESSION:" + (resp.portalId() != null ? resp.portalId() : "unknown");
            kafkaTemplate.send(TOPIC, key, payload);
            log.debug("[PalletSession] Evento {} publicado | portal={} epc={}",
                    resp.resultType(), resp.portalId(), resp.epc());
        } catch (Exception e) {
            log.error("[PalletSession] No se pudo publicar a Kafka: {}", e.getMessage());
        }
    }
}
