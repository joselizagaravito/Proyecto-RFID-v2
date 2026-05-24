package com.pystelectronic.rfid.transfer.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pystelectronic.rfid.transfer.audit.dto.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String TOPIC = "transfer.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogService(KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper  = objectMapper;
    }

    @Async
    public void saveAsync(AuditLogEntry entry) {
        try {
            String payload = objectMapper.writeValueAsString(entry);
            String key = "AUDIT:" + (entry.getUserId() != null ? entry.getUserId() : "unknown");
            kafkaTemplate.send(TOPIC, key, payload);
        } catch (Exception e) {
            log.error("[AuditLogService] No se pudo publicar a Kafka: {}", e.getMessage());
        }
    }
}