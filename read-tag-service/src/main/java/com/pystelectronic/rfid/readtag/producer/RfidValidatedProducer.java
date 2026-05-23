package com.pystelectronic.rfid.readtag.producer;

import com.pystelectronic.rfid.readtag.dto.ValidatedReadEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publica eventos al topic rfid.validated después de que read-tag-service
 * persiste exitosamente una lectura RFID cruda.
 *
 * La clave del mensaje Kafka es el EPC del tag para garantizar que
 * todas las lecturas del mismo tag vayan a la misma partición
 * (preserva el orden de procesamiento por tag en validation-service).
 *
 * Publicación asíncrona con callback: si falla la publicación, se registra
 * el error con el correlationId para trazabilidad, pero NO se hace rollback
 * de la persistencia — la lectura ya está en PostgreSQL y es recuperable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RfidValidatedProducer {

    private final KafkaTemplate<String, ValidatedReadEvent> validatedKafkaTemplate;

    @Value("${rfid.kafka.topics.validated}")
    private String validatedTopic;

    /**
     * Publica un evento ValidatedReadEvent al topic rfid.validated.
     *
     * @param event Evento con los datos de la lectura ya persistida
     */
    public void publish(ValidatedReadEvent event) {
        CompletableFuture<SendResult<String, ValidatedReadEvent>> future =
                validatedKafkaTemplate.send(validatedTopic, event.getEpc(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[{}] Error publicando EPC={} a {}: {}",
                        event.getCorrelationId(),
                        event.getEpc(),
                        validatedTopic,
                        ex.getMessage(), ex);
            } else {
                log.debug("[{}] EPC={} publicado a {}[partition={}][offset={}]",
                        event.getCorrelationId(),
                        event.getEpc(),
                        validatedTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
