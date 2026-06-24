package com.pystelectronic.rfid.readtag.service;

import com.pystelectronic.rfid.readtag.dto.RawReadMessage;
import com.pystelectronic.rfid.readtag.dto.ReadTagRequest;
import com.pystelectronic.rfid.readtag.dto.ReadTagResponse;
import com.pystelectronic.rfid.readtag.entity.ReadTag;
import com.pystelectronic.rfid.readtag.repository.ReadTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Async;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadTagService {

    private final ReadTagRepository repository;
    private final ReadTagMapper mapper;
    private final ReadTagPersistenceService persistenceService;
    private final PalletSessionClient palletSessionClient;

    @Transactional(readOnly = true)
    public Page<ReadTagResponse> findAll(String epc, String moduloId,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return repository.findWithFilters(epc, moduloId, startDate, endDate, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ReadTagResponse findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Lectura RFID no encontrada con id=" + id));
    }

    @Transactional
    public ReadTagResponse create(ReadTagRequest request, String correlationId) {
        String corrId = resolveCorrelationId(correlationId);
        RawReadMessage message = mapper.toRawReadMessage(request);
        ReadTag saved = persistenceService.saveOrUpdate(message, corrId);
        // Notificar sesión de pallet de forma asíncrona (no bloquea la respuesta al C#)
        palletSessionClient.notifySession(request.getModuloId(), request.getEpc());
        return mapper.toResponse(saved);
    }

    @Transactional
    public List<ReadTagResponse> createBatch(List<ReadTagRequest> requests, String correlationId) {
        String corrId = resolveCorrelationId(correlationId);
        List<RawReadMessage> messages = requests.stream().map(mapper::toRawReadMessage).toList();
        return persistenceService.saveOrUpdateBatch(messages, corrId).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional
    public ReadTagResponse update(UUID id, ReadTagRequest request, String correlationId) {
        ReadTag existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Lectura RFID no encontrada con id=" + id));
        mapper.updateEntity(existing, request);
        return mapper.toResponse(repository.save(existing));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Lectura RFID no encontrada con id=" + id);
        }
        repository.deleteById(id);
    }

    private String resolveCorrelationId(String correlationId) {
        return (correlationId != null && !correlationId.isBlank())
                ? correlationId : UUID.randomUUID().toString();
    }
}