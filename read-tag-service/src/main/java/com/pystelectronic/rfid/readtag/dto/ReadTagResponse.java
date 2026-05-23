package com.pystelectronic.rfid.readtag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadTagResponse {

    private UUID id;
    private String epc;
    private String tag;
    private String tid;
    private Integer invTimes;
    private Integer rssi;
    private Integer antId;
    private LocalDateTime lastTime;
    private LocalDateTime firstUpdate;
    private String color;
    private String moduloId;
    private String moduloRol;
}