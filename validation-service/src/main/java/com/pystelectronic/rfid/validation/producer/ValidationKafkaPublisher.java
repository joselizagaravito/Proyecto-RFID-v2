package com.pystelectronic.rfid.validation.producer;

import com.pystelectronic.rfid.validation.dto.TransferStateEvent;
import com.pystelectronic.rfid.validation.dto.ValidatedReadEvent;
import com.pystelectronic.rfid.validation.service.ValidationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica eventos en los topics Kafka de salida del validation-service:
 *  - rfid.validated     → realtime-service, alert-service
 *  - transfer.events    → realtime-service, audit-service
 *  - transfer.alerts    → alert-service (anomalías)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationKafkaPublisher implements ValidationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${rfid.kafka.topics.validated}")
    private String topicValidated;

    @Value("${rfid.kafka.topics.transfer-events}")
    private String topicTransferEvents;

    @Value("${rfid.kafka.topics.transfer-alerts}")
    private String topicTransferAlerts;

    @Override
    public void publishValidated(ValidatedReadEvent event) {
        kafkaTemplate.send(topicValidated, event.getTransferId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error publicando en {}: correlationId={} error={}",
                                topicValidated, event.getCorrelationId(), ex.getMessage());
                    } else {
                        log.debug("Publicado en {}: EPC={} correlationId={}",
                                topicValidated, event.getEpc(), event.getCorrelationId());
                    }
                });
    }

    @Override
    public void publishTransferEvent(TransferStateEvent event) {
        kafkaTemplate.send(topicTransferEvents, event.getTransferId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error publicando en {}: correlationId={} error={}",
                                topicTransferEvents, event.getCorrelationId(), ex.getMessage());
                    }
                });
    }

    @Override
    public void publishAlert(ValidatedReadEvent alertEvent) {
        kafkaTemplate.send(topicTransferAlerts, alertEvent.getEpc(), alertEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error publicando alerta en {}: EPC={} error={}",
                                topicTransferAlerts, alertEvent.getEpc(), ex.getMessage());
                    } else {
                        log.warn("Alerta publicada: tipo={} EPC={} correlationId={}",
                                alertEvent.getValidationResult(),
                                alertEvent.getEpc(),
                                alertEvent.getCorrelationId());
                    }
                });
    }
}
