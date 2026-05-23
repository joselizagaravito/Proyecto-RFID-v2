package com.pystelectronic.rfid.readtag.consumer;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import com.pystelectronic.rfid.readtag.service.ReadTagPersistenceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consumidor Kafka del topic rfid.raw-reads.
 *
 * Sprint 4: agrega propagación del correlationId desde el header Kafka
 * "X-Correlation-Id". Si el hardware no lo envía, se genera un UUID
 * para garantizar trazabilidad end-to-end en todos los logs y eventos.
 *
 * Lee en batch (hasta 100 mensajes por poll) para mayor throughput.
 * Usa MANUAL_IMMEDIATE ack: el offset se confirma solo después de que
 * la transacción de base de datos y la publicación hayan completado.
 */
@Slf4j
@Component
public class RawReadsConsumer {

    private final ReadTagPersistenceService persistenceService;
    private final Validator validator;

    private final Counter messagesProcessed;
    private final Counter messagesInvalid;
    private final Counter messagesError;

    public RawReadsConsumer(
            ReadTagPersistenceService persistenceService,
            Validator validator,
            MeterRegistry meterRegistry) {
        this.persistenceService = persistenceService;
        this.validator = validator;
        this.messagesProcessed = Counter.builder("rfid.read_tag.processed")
                .description("Lecturas RFID crudas procesadas y persistidas")
                .register(meterRegistry);
        this.messagesInvalid = Counter.builder("rfid.read_tag.invalid")
                .description("Lecturas RFID rechazadas por validación")
                .register(meterRegistry);
        this.messagesError = Counter.builder("rfid.read_tag.errors")
                .description("Errores al persistir lecturas RFID")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${rfid.kafka.topics.raw-reads}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consume(
            List<ConsumerRecord<String, RawReadMessage>> records,
            Acknowledgment ack) {

        // Extrae correlationId del primer mensaje del batch (todos comparten contexto de portal)
        String correlationId = extractCorrelationId(records.isEmpty() ? null : records.get(0));

        log.debug("[{}] Batch recibido: {} mensajes del topic rfid.raw-reads",
                correlationId, records.size());

        List<RawReadMessage> validMessages = records.stream()
                .map(ConsumerRecord::value)
                .filter(this::isValid)
                .toList();

        if (!validMessages.isEmpty()) {
            try {
                persistenceService.saveOrUpdateBatch(validMessages, correlationId);
                messagesProcessed.increment(validMessages.size());
                log.info("[{}] Batch persistido: {}/{} lecturas válidas",
                        correlationId, validMessages.size(), records.size());
            } catch (Exception ex) {
                messagesError.increment(records.size());
                log.error("[{}] Error persistiendo batch de {} lecturas: {}",
                        correlationId, records.size(), ex.getMessage(), ex);
                // No hacemos ack → Kafka reintentará el batch.
                // saveOrUpdate es idempotente por upsert de EPC.
                return;
            }
        }

        ack.acknowledge();
    }

    // ── Métodos privados ──────────────────────────────────────────────────────

    /**
     * Extrae el header "X-Correlation-Id" del registro Kafka.
     * Si no existe, genera un UUID nuevo para garantizar trazabilidad.
     */
    private String extractCorrelationId(ConsumerRecord<String, RawReadMessage> record) {
        if (record == null) return UUID.randomUUID().toString();
        Header header = record.headers().lastHeader("X-Correlation-Id");
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return UUID.randomUUID().toString();
    }

    private boolean isValid(RawReadMessage message) {
        if (message == null) {
            log.warn("Mensaje nulo recibido en rfid.raw-reads, descartando");
            messagesInvalid.increment();
            return false;
        }
        Set<ConstraintViolation<RawReadMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            violations.forEach(v ->
                    log.warn("Lectura inválida descartada — campo: {}, error: {}, valor: {}",
                            v.getPropertyPath(), v.getMessage(), v.getInvalidValue()));
            messagesInvalid.increment();
            return false;
        }
        return true;
    }
}
