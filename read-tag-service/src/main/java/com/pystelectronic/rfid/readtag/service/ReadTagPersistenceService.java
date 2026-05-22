package com.pystelectronic.rfid.readtag.service;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import com.pystelectronic.rfid.readtag.entity.ReadTag;
import com.pystelectronic.rfid.readtag.repository.ReadTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lógica de negocio para persistir lecturas RFID crudas.
 *
 * Estrategia de actualización:
 * - Si el EPC ya existe → actualiza last_time, rssi, inv_times (upsert lógico).
 * - Si es nuevo → inserta registro completo.
 *
 * Esta estrategia evita duplicados por re-lectura del mismo tag en la misma sesión,
 * que es el comportamiento normal de portales RFID de alta frecuencia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadTagPersistenceService {

    private final ReadTagRepository repository;
    private final ReadTagMapper mapper;

    /**
     * Persiste una lectura individual. Realiza upsert por EPC.
     *
     * @param message Mensaje deserializado desde Kafka
     */
    @Transactional
    public ReadTag saveOrUpdate(RawReadMessage message) {
        return repository.findByEpc(message.getEpc())
                .map(existing -> updateExisting(existing, message))
                .orElseGet(() -> insertNew(message));
    }

    /**
     * Persiste un lote de lecturas. Cada una se procesa con upsert por EPC.
     * Se usa en consumo por batch (max-poll-records).
     */
    @Transactional
    public List<ReadTag> saveOrUpdateBatch(List<RawReadMessage> messages) {
        return messages.stream()
                .map(this::saveOrUpdate)
                .toList();
    }

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
        ReadTag entity = mapper.toEntity(msg);
        return repository.save(entity);
    }
}
