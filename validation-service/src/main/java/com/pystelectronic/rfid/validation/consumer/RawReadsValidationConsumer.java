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

/**
 * Consumidor Kafka del topic rfid.raw-reads para el validation-service.
 *
 * Nota: Tanto read-tag-service como validation-service consumen el mismo topic
 * pero con diferentes consumer group IDs, de modo que ambos reciben todos los mensajes.
 * - read-tag-service-group   → persiste la lectura cruda
 * - validation-service-group → valida el EPC y publica eventos
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawReadsValidationConsumer {

    private final EpcValidationService validationService;

    @KafkaListener(
            topics = "${rfid.kafka.topics.raw-reads}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, RawReadMessage> record,
            Acknowledgment ack) {

        RawReadMessage msg = record.value();
        if (msg == null || msg.getEpc() == null) {
            log.warn("Mensaje nulo o sin EPC recibido, descartando");
            ack.acknowledge();
            return;
        }

        try {
            String deviceRole = inferDeviceRole(msg.getModuloRol());
            Instant readAt = msg.getLastTime() != null ? msg.getLastTime() : Instant.now();

            validationService.validate(msg.getEpc(), msg.getModuloId(), deviceRole, readAt);
            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Error validando EPC={}: {}", msg.getEpc(), ex.getMessage(), ex);
            // No confirmamos el offset para reintentar.
            // En producción considerar DLQ para evitar bloqueo.
        }
    }

    /**
     * Infiere el rol del dispositivo a partir del moduloRol del hardware.
     * Convención: "puerta1"/"puerta2" son portales, el resto son handhelds.
     */
    private String inferDeviceRole(String moduloRol) {
        if (moduloRol == null) return "HANDHELD";
        String lower = moduloRol.toLowerCase();
        if (lower.contains("puerta") || lower.contains("gate")) {
            return lower.contains("out") || lower.contains("salida") ? "GATE_OUT" : "GATE_IN";
        }
        return "HANDHELD";
    }
}
