package com.pystelectronic.rfid.readtag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entidad JPA que mapea la tabla read_tag.
 * Preserva la estructura original del prototipo para compatibilidad
 * con el hardware lector existente.
 */
@Entity
@Table(name = "read_tag")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Código EPC extendido del tag RFID.
     * El lector puede enviar EPCs más largos que el estándar EPC-96 (24 hex).
     * Se acepta hasta 96 caracteres para compatibilidad con lectores que
     * formatean el EPC con prefijos.
     */
    @Column(name = "epc", nullable = false, length = 96)
    private String epc;

    /** Descripción legible del tag (nombre del producto, categoría, etc.). */
    @Column(name = "tag", length = 100)
    private String tag;

    /** Tag Identifier: identificador único de hardware del chip RFID. */
    @Column(name = "tid", length = 100)
    private String tid;

    /** Número de sesiones de inventario en que fue detectado. */
    @Column(name = "inv_times")
    private Integer invTimes;

    /** Potencia de señal en dBm. Valores típicos: -80 a -20 dBm. */
    @Column(name = "rssi")
    private Integer rssi;

    /** ID de la antena lectora (1-4 en la mayoría de portales de 4 antenas). */
    @Column(name = "ant_id")
    private Integer antId;

    /** Timestamp de la última lectura registrada para este EPC. */
    @Column(name = "last_time")
    private LocalDateTime lastTime;

    /** Timestamp de la primera lectura registrada para este EPC en la sesión. */
    @Column(name = "first_update")
    private LocalDateTime firstUpdate;

    /** Color para clasificación visual en el dashboard (ej: "green", "red"). */
    @Column(name = "color", length = 30)
    private String color;

    /** Identificador del módulo lector (ej: "GATE-OUT-01", "HANDHELD-02"). */
    @Column(name = "modulo_id", length = 40)
    private String moduloId;

    /** Rol del módulo (ej: "puerta1", "puerta2", "handheld"). */
    @Column(name = "modulo_rol", length = 40)
    private String moduloRol;
}
