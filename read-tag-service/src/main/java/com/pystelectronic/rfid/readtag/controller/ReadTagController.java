package com.pystelectronic.rfid.readtag.controller;

import com.pystelectronic.rfid.readtag.dto.ReadTagRequest;
import com.pystelectronic.rfid.readtag.dto.ReadTagResponse;
import com.pystelectronic.rfid.readtag.service.ReadTagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/read-tags")
@RequiredArgsConstructor
public class ReadTagController {

    private final ReadTagService readTagService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String epc,
            @RequestParam(required = false) String moduloId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "lastTime"));
        Page<ReadTagResponse> result = readTagService.findAll(epc, moduloId, startDate, endDate, pageable);
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalPages", result.getTotalPages(),
                "last", result.isLast()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReadTagResponse> getById(@PathVariable UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(readTagService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ReadTagResponse> create(
            @Valid @RequestBody ReadTagRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(readTagService.create(request, correlationId));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> createBatch(
            @Valid @RequestBody List<@Valid ReadTagRequest> requests,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        if (requests.isEmpty()) return ResponseEntity.badRequest()
                .body(Map.of("error", "El lote no puede estar vacio"));
        if (requests.size() > 1000) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "El lote no puede superar 1000 lecturas"));
        List<ReadTagResponse> saved = readTagService.createBatch(requests, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("saved", saved.size(), "content", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ReadTagResponse> update(@PathVariable UUID id,
            @Valid @RequestBody ReadTagRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return ResponseEntity.ok(readTagService.update(id, request, correlationId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        readTagService.delete(id);
        return ResponseEntity.noContent().build();
    }
}