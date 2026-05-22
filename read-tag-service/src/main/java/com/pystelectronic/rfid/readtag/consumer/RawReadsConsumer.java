package com.pystelectronic.rfid.readtag.consumer;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import com.pystelectronic.rfid.readtag.service.ReadTagPersistenceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Consumidor Kafka del topic rfid.raw-reads.
 *
 * Lee en batch (hasta 100 mensajes por poll) para mayor throughput.
 * Valida cada mensaje antes de persistir; los inválidos se descartan con log
 * de advertencia (dead-letter queue puede agregarse en Sprint 3).
 *
 * Usa MANUAL_IMMEDIATE ack: el offset se confirma solo después de que
 * la transacción de base de datos haya completado con éxito.
 */
@Slf4j
@Component
public class RawReadsConsumer {

    private final ReadTagPersistenceService persistenceService;
    private final Validator validator;

    // Métricas Micrometer
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

    /**
     * Listener batch del topic rfid.raw-reads.
     *
     * @param records  Lista de registros Kafka recibidos en el poll
     * @param ack      Acknowledgment manual — se confirma al finalizar el batch
     */
    @KafkaListener(
            topics = "${rfid.kafka.topics.raw-reads}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consume(
            List<ConsumerRecord<String, RawReadMessage>> records,
            Acknowledgment ack) {

        log.debug("Batch recibido: {} mensajes del topic rfid.raw-reads", records.size());

        List<RawReadMessage> validMessages = records.stream()
                .map(ConsumerRecord::value)
                .filter(this::isValid)
                .toList();

        if (!validMessages.isEmpty()) {
            try {
                persistenceService.saveOrUpdateBatch(validMessages);
                messagesProcessed.increment(validMessages.size());
                log.info("Batch persistido: {}/{} lecturas válidas",
                        validMessages.size(), records.size());
            } catch (Exception ex) {
                messagesError.increment(records.size());
                log.error("Error persistiendo batch de {} lecturas: {}",
                        records.size(), ex.getMessage(), ex);
                // No hacemos ack → Kafka reintentará el batch completo.
                // El servicio es idempotente por upsert de EPC, por lo que
                // un reintento es seguro.
                return;
            }
        }

        // Confirmamos offset independientemente de cuántos fueron inválidos
        ack.acknowledge();
    }

    /**
     * Valida el mensaje con Jakarta Bean Validation.
     * Mensajes inválidos se descartan y se registra una advertencia.
     */
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
