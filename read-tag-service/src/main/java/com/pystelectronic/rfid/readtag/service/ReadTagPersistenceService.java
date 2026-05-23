package com.pystelectronic.rfid.readtag.service;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import com.pystelectronic.rfid.readtag.dto.ValidatedReadEvent;
import com.pystelectronic.rfid.readtag.entity.ReadTag;
import com.pystelectronic.rfid.readtag.producer.RfidValidatedProducer;
import com.pystelectronic.rfid.readtag.repository.ReadTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Lógica de negocio para persistir lecturas RFID crudas y publicar
 * el evento rfid.validated al pipeline Kafka.
 *
 * Flujo por cada lectura:
 *   1. Upsert en tabla read_tag (insert si es EPC nuevo, update si ya existe).
 *   2. Publicar ValidatedReadEvent al topic rfid.validated (asíncrono).
 *
 * La publicación Kafka es asíncrona — si falla, la lectura YA está en
 * PostgreSQL. El log de error con correlationId permite recuperación manual
 * o un job de reprocessing futuro. No se hace rollback de la BD.
 *
 * Estrategia de upsert:
 *   - EPC nuevo → inserta registro completo.
 *   - EPC existente → actualiza last_time, rssi, inv_times.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadTagPersistenceService {

    private final ReadTagRepository repository;
    private final ReadTagMapper mapper;
    private final RfidValidatedProducer validatedProducer;

    /**
     * Persiste una lectura y publica evento rfid.validated.
     *
     * @param message       Mensaje deserializado desde Kafka o recibido por REST
     * @param correlationId ID de trazabilidad end-to-end (propagado desde header Kafka o generado)
     */
    @Transactional
    public ReadTag saveOrUpdate(RawReadMessage message, String correlationId) {
        ReadTag saved = repository.findByEpc(message.getEpc())
                .map(existing -> updateExisting(existing, message))
                .orElseGet(() -> insertNew(message));

        publishValidatedEvent(saved, correlationId);
        return saved;
    }

    /**
     * Persiste un lote de lecturas. Cada una hace upsert y publica evento.
     * Transacción única — todo el batch o nada.
     */
    @Transactional
    public List<ReadTag> saveOrUpdateBatch(List<RawReadMessage> messages, String correlationId) {
        return messages.stream()
                .map(msg -> saveOrUpdate(msg, correlationId))
                .toList();
    }

    // ── Métodos privados ──────────────────────────────────────────────────────

    private ReadTag updateExisting(ReadTag existing, RawReadMessage msg) {
        log.debug("Actualizando lectura existente para EPC={}", msg.getEpc());
        existing.setInvTimes(msg.getInvTimes() != null
                ? msg.getInvTimes()
                : (existing.getInvTimes() != null ? existing.getInvTimes() + 1 : 1));
        existing.setRssi(msg.getRssi());
        existing.setLastTime(mapper.toLocalDateTime(msg.getLastTime()));
        if (msg.getColor() != null) existing.setColor(msg.getColor());
        return repository.save(existing);
    }

    private ReadTag insertNew(RawReadMessage msg) {
        log.debug("Insertando nueva lectura para EPC={}", msg.getEpc());
        return repository.save(mapper.toEntity(msg));
    }

    private void publishValidatedEvent(ReadTag saved, String correlationId) {
        ValidatedReadEvent event = ValidatedReadEvent.builder()
                .epc(saved.getEpc())
                .readTagId(saved.getId())
                .deviceId(saved.getModuloId())
                .portalLocation(saved.getModuloRol())
                .rssi(saved.getRssi())
                .readAt(saved.getLastTime() != null
                        ? saved.getLastTime().toInstant(java.time.ZoneOffset.UTC)
                        : Instant.now())
                .correlationId(correlationId)
                .build();

        validatedProducer.publish(event);
    }
}
