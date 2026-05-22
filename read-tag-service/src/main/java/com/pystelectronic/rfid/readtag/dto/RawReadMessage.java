package com.pystelectronic.rfid.readtag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mensaje Kafka del topic rfid.raw-reads.
 * Enviado por portales RFID (WebSocket) y PDAs (HTTP REST).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawReadMessage {

    /**
     * EPC-96 en hexadecimal (24 caracteres). Campo principal.
     * Regex: ^[0-9A-F]{24}$
     */
    @NotBlank(message = "El campo epc es obligatorio")
    @Pattern(regexp = "^[0-9A-F]{24}$", message = "EPC debe ser hexadecimal de 24 caracteres mayúsculos")
    private String epc;

    /** Descripción textual del tag (opcional, enviada por algunos lectores). */
    @Size(max = 100)
    private String tag;

    /** Tag Identifier del chip (TID), identificador único de hardware. */
    @Size(max = 100)
    private String tid;

    /** Cuántas veces fue inventariado en esta sesión de lectura. */
    private Integer invTimes;

    /** Potencia de señal recibida en dBm (Received Signal Strength Indicator). */
    private Integer rssi;

    /** Identificador de la antena que realizó la lectura. */
    private Integer antId;

    /** Timestamp de la última lectura de este EPC en la sesión. */
    private Instant lastTime;

    /** Timestamp de la primera lectura de este EPC en la sesión. */
    private Instant firstUpdate;

    /** Indicador de color para clasificación visual en el dashboard. */
    @Size(max = 30)
    private String color;

    /** ID del módulo/lector RFID que generó la lectura. */
    @Size(max = 40)
    private String moduloId;

    /** Rol del módulo: puerta1, puerta2, handheld, etc. */
    @Size(max = 40)
    private String moduloRol;
}
