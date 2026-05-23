package com.pystelectronic.rfid.readtag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "read_tag")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadTag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "epc", nullable = false, length = 96)
    private String epc;

    @Column(name = "tag", length = 100)
    private String tag;

    @Column(name = "tid", length = 100)
    private String tid;

    @Column(name = "inv_times")
    private Integer invTimes;

    @Column(name = "rssi")
    private Integer rssi;

    @Column(name = "ant_id")
    private Integer antId;

    @Column(name = "last_time")
    private LocalDateTime lastTime;

    @Column(name = "first_update")
    private LocalDateTime firstUpdate;

    @Column(name = "color", length = 30)
    private String color;

    @Column(name = "modulo_id", length = 40)
    private String moduloId;

    @Column(name = "modulo_rol", length = 40)
    private String moduloRol;
}