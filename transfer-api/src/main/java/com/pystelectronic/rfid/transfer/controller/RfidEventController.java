package com.pystelectronic.rfid.transfer.controller;

import com.pystelectronic.rfid.transfer.entity.RfidEvent;
import com.pystelectronic.rfid.transfer.repository.RfidEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * GET /api/v1/rfid-events  — lista paginada de eventos RFID.
 * GET /api/v1/rfid-events/{id} — detalle de un evento.
 *
 * Sprint 3: implementación básica sobre la tabla rfid_event.
 * En Sprint 5 este endpoint se enriquecerá con datos en tiempo real vía Redis.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rfid-events")
@RequiredArgsConstructor
public class RfidEventController {

    private final RfidEventRepository rfidEventRepository;

    @GetMapping
    public ResponseEntity<?> listEvents(
            @RequestParam(defaultValue = "0")   int    page,
            @RequestParam(defaultValue = "15")  int    size,
            @RequestParam(required = false)     String eventType,
            @RequestParam(required = false)     String result,
            @RequestParam(required = false)     String lpnCode,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        log.debug("[{}] GET /rfid-events page={} size={} eventType={} result={} lpnCode={}",
            correlationId, page, size, eventType, result, lpnCode);

        // Normalizar parámetros vacíos a null para el query
        String evtTypeParam = (eventType != null && !eventType.isBlank()) ? eventType : null;
        String resultParam  = (result    != null && !result.isBlank())    ? result    : null;
        String lpnParam     = (lpnCode   != null && !lpnCode.isBlank())   ? lpnCode   : null;

        var pageable = PageRequest.of(page, Math.min(size, 50),
            Sort.by(Sort.Direction.DESC, "timestamp"));

        Page<RfidEvent> pageResult = rfidEventRepository.findWithFilters(
            evtTypeParam, resultParam, lpnParam, pageable);

        return ResponseEntity.ok(Map.of(
            "content",       pageResult.getContent(),
            "totalElements", pageResult.getTotalElements(),
            "page",          pageResult.getNumber(),
            "size",          pageResult.getSize(),
            "totalPages",    pageResult.getTotalPages(),
            "last",          pageResult.isLast()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEvent(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        log.debug("[{}] GET /rfid-events/{}", correlationId, id);

        return rfidEventRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}