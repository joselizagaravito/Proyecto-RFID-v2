package com.pystelectronic.rfid.transfer.audit.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

@Getter
@Builder
public class AuditLogEntry {
    private String correlationId;
    private String idempotencyKey;
    private String userId;
    private String userRoles;
    private String clientIp;
    private String userAgent;
    private String httpMethod;
    private String endpointPath;
    private String queryParams;
    private String requestSummary;
    private short  httpStatus;
    private String errorCode;
    private int    durationMs;
    private AuditLevel auditLevel;
    private OffsetDateTime createdAt;
}