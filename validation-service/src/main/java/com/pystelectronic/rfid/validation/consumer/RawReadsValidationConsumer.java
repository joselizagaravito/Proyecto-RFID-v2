package com.pystelectronic.rfid.validation.consumer;

import com.pystelectronic.rfid.validation.dto.RawReadMessage;
import com.pystelectronic.rfid.validation.service.EpcValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RawReadsValidationConsumer {

    private final EpcValidationService validationService;

    @KafkaListener(
            topics           = "${rfid.kafka.topics.raw-reads:rfid.raw-reads}",
            groupId          = "${spring.kafka.consumer.group-id:validation-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, RawReadMessage> record,
                        Acknowledgment ack) {

        RawReadMessage msg = record.value();

        if (msg == null || msg.epc() == null) {
            log.warn("Mensaje nulo o sin EPC en offset={} partition={} — ignorado",
                    record.offset(), record.partition());
            ack.acknowledge();
            return;
        }

        try {
            // portalId actúa como deviceId; rol inferido desde el portalId
            String deviceRole = inferDeviceRole(msg.portalId());
            Instant readAt    = msg.readAt() != null ? msg.readAt() : Instant.now();

            validationService.validate(msg.epc(), msg.portalId(), deviceRole, readAt);
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("[{}] Error validando EPC={}: {}",
                    msg.correlationId(), msg.epc(), ex.getMessage(), ex);
            ack.acknowledge(); // commit para no bloquear el consumer group
        }
    }

    private String inferDeviceRole(String portalId) {
        if (portalId == null) return "HANDHELD";
        String lower = portalId.toLowerCase();
        if (lower.contains("out") || lower.contains("salida")) return "GATE_OUT";
        if (lower.contains("in")  || lower.contains("entrada")) return "GATE_IN";
        if (lower.contains("gate") || lower.contains("puerta")) return "GATE_IN";
        return "HANDHELD";
    }
}