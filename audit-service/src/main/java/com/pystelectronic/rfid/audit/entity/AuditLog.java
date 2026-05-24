package com.pystelectronic.rfid.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false, length = 40)
    private String userId;

    @Column(name = "user_roles", nullable = false, length = 200)
    private String userRoles;

    @Column(name = "client_ip", nullable = false, length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "endpoint_path", nullable = false, length = 200)
    private String endpointPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_params", columnDefinition = "jsonb")
    private String queryParams;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_summary", columnDefinition = "jsonb")
    private String requestSummary;

    @Column(name = "http_status", nullable = false)
    private short httpStatus;

    @Column(name = "error_code", length = 20)
    private String errorCode;

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "audit_level", nullable = false, length = 10)
    private String auditLevel;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}