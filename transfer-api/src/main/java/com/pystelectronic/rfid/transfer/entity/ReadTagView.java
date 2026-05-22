package com.pystelectronic.rfid.transfer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Vista de solo lectura de la tabla read_tag.
 * Mapea la estructura REAL de la tabla en PostgreSQL.
 * La tabla es escrita por read-tag-service vía Kafka.
 * transfer-api solo la expone en lectura en GET /api/v1/read-tags.
 */
@Entity
@Table(name = "read_tag")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReadTagView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "lpn_id")
    private UUID lpnId;

    @Column(name = "lpn_code", length = 14)
    private String lpnCode;

    @Column(name = "epc", length = 24)
    private String epc;

    @Column(name = "device_id", nullable = false, length = 40)
    private String deviceId;

    @Column(name = "device_type", nullable = false, length = 20)
    private String deviceType;

    @Column(name = "user_id", nullable = false, length = 40)
    private String userId;

    @Column(name = "portal_location", length = 100)
    private String portalLocation;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "read_datetime", nullable = false)
    private OffsetDateTime readDatetime;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}