package com.pystelectronic.rfid.audit.controller;

import com.pystelectronic.rfid.audit.entity.AuditLog;
import com.pystelectronic.rfid.audit.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogRepository repo;
    public AuditLogController(AuditLogRepository repo) { this.repo = repo; }

    @GetMapping
    public ResponseEntity<Page<AuditLog>> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) String auditLevel,
            @RequestParam(required = false) String endpointPath,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        if (ChronoUnit.DAYS.between(startDate, endDate) > 31)
            return ResponseEntity.badRequest().build();

        String pathFilter = (endpointPath != null && !endpointPath.isBlank())
            ? endpointPath.replace("*", "%") : null;
        Short statusFilter = httpStatus != null ? httpStatus.shortValue() : null;

        return ResponseEntity.ok(repo.buscarConFiltros(
            startDate, endDate, userId, clientIp, httpMethod,
            statusFilter, auditLevel, pathFilter,
            PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "createdAt"))
        ));
    }
}