package com.pystelectronic.rfid.transfer.controller;

import com.pystelectronic.rfid.transfer.entity.ReadTagView;
import com.pystelectronic.rfid.transfer.repository.ReadTagQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/read-tags")
@RequiredArgsConstructor
public class ReadTagController {

    private final ReadTagQueryRepository readTagRepository;

    @GetMapping
    public ResponseEntity<?> listReadTags(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size,
            @RequestParam(required = false)    String epc,
            @RequestParam(required = false)    String deviceId,
            @RequestParam(required = false)    String eventType,
            @RequestParam(required = false)    String result,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        log.debug("[{}] GET /read-tags page={} size={}", correlationId, page, size);

        String epcParam       = (epc       != null && !epc.isBlank())       ? epc       : null;
        String deviceIdParam  = (deviceId  != null && !deviceId.isBlank())  ? deviceId  : null;
        String eventTypeParam = (eventType != null && !eventType.isBlank()) ? eventType : null;
        String resultParam    = (result    != null && !result.isBlank())    ? result    : null;

        // Sort.unsorted() para que no interfiera con el ORDER BY fijo de la query nativa
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.unsorted());

        Page<ReadTagView> pageResult = readTagRepository.findWithFilters(
            epcParam, deviceIdParam, eventTypeParam, resultParam, pageable);

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
    public ResponseEntity<ReadTagView> getReadTag(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        log.debug("[{}] GET /read-tags/{}", correlationId, id);
        return readTagRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}