package com.pystelectronic.rfid.validation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Copia local del mensaje Kafka rfid.raw-reads en el validation-service.
 * Se mantiene como copia independiente para evitar acoplamiento de jar
 * entre microservicios. En un monorepo con shared-lib esto sería el mismo DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawReadMessage {
    private String epc;
    private String tag;
    private String tid;
    private Integer invTimes;
    private Integer rssi;
    private Integer antId;
    private Instant lastTime;
    private Instant firstUpdate;
    private String color;
    private String moduloId;
    private String moduloRol;
}
