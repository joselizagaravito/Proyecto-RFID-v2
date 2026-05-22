package com.pystelectronic.rfid.transfer.entity;

import com.pystelectronic.rfid.common.enums.RfidEventType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad JPA para la tabla rfid_event.
 * Registra cada evento de lectura/validación RFID asociado a un traslado.
 * Creada en Sprint 3 para exponer GET /api/v1/rfid-events.
 */
@Entity
@Table(name = "rfid_event", indexes = {
    @Index(name = "idx_rfid_event_transfer", columnList = "transfer_id"),
    @Index(name = "idx_rfid_event_timestamp", columnList = "timestamp"),
    @Index(name = "idx_rfid_event_lpn", columnList = "lpn_code")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RfidEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "event_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RfidEventType eventType;

    @Column(name = "epc", length = 96)
    private String epc;

    @Column(name = "lpn_code", length = 20)
    private String lpnCode;

    @Column(name = "device_id", length = 40)
    private String deviceId;

    @Column(name = "user_id", length = 40)
    private String userId;

    @Column(name = "location", length = 50)
    private String location;

    @Column(name = "result", length = 20)
    private String result;

    @Column(name = "error_code", length = 20)
    private String errorCode;

    @Column(name = "timestamp", nullable = false)
    private OffsetDateTime timestamp;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;
}