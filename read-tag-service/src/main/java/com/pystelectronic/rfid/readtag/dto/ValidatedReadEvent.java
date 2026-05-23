package com.pystelectronic.rfid.readtag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidatedReadEvent {

    private String epc;
    private UUID readTagId;
    private String deviceId;
    private String portalLocation;
    private Integer rssi;
    private Instant readAt;
    private String correlationId;
}