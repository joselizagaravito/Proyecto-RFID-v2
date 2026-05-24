package com.pystelectronic.rfid.audit.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pystelectronic.rfid.audit.entity.AuditLog;
import com.pystelectronic.rfid.audit.repository.AuditLogRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(AuditLogRepository auditLogRepository,
                               ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "transfer.events", groupId = "rfid-audit-service", concurrency = "2")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String key = record.key();
        if (key == null || !key.startsWith("AUDIT:")) { ack.acknowledge(); return; }
        try {
            AuditLog entry = mapToEntity(objectMapper.readTree(record.value()));
            auditLogRepository.save(entry);
            log.debug("[AuditConsumer] Guardado user:{} method:{} path:{} status:{}",
                entry.getUserId(), entry.getHttpMethod(),
                entry.getEndpointPath(), entry.getHttpStatus());
        } catch (Exception e) {
            log.error("[AuditConsumer] Error: key={} error={}", key, e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }

    private AuditLog mapToEntity(JsonNode n) {
        AuditLog e = new AuditLog();
        if (n.hasNonNull("correlationId")) {
            try { e.setCorrelationId(UUID.fromString(n.get("correlationId").asText())); }
            catch (IllegalArgumentException ignored) {}
        }
        e.setIdempotencyKey(t(n,"idempotencyKey",null));
        e.setUserId(t(n,"userId","unknown"));
        e.setUserRoles(t(n,"userRoles",""));
        e.setClientIp(t(n,"clientIp","0.0.0.0"));
        e.setUserAgent(t(n,"userAgent",null));
        e.setHttpMethod(t(n,"httpMethod","UNKNOWN"));
        e.setEndpointPath(t(n,"endpointPath","/"));
        e.setQueryParams(t(n,"queryParams",null));
        e.setRequestSummary(t(n,"requestSummary",null));
        e.setHttpStatus((short) n.path("httpStatus").asInt(0));
        e.setErrorCode(t(n,"errorCode",null));
        e.setDurationMs(n.path("durationMs").asInt(0));
        e.setAuditLevel(t(n,"auditLevel","READ"));
        String ts = t(n,"createdAt",null);
        try { e.setCreatedAt(ts!=null ? OffsetDateTime.parse(ts) : OffsetDateTime.now()); }
        catch (Exception ex) { e.setCreatedAt(OffsetDateTime.now()); }
        return e;
    }

    private String t(JsonNode n, String f, String d) {
        return n.hasNonNull(f) ? n.get(f).asText(d) : d;
    }
}