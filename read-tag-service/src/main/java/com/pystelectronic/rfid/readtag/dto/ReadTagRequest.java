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
 * Request para POST /api/v1/read-tags y PUT /api/v1/read-tags/{id}.
 *
 * Usado por PDAs y handhelds que envían lecturas RFID directamente
 * por HTTP REST (sin pasar por Kafka). El controlador persiste la lectura
 * y publica el evento rfid.validated igual que el flujo Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadTagRequest {

    @NotBlank(message = "El campo epc es obligatorio")
    @Pattern(regexp = "^[0-9A-Za-z]{1,36}$",
             message = "EPC debe ser hexadecimal de 24 caracteres mayúsculos")
    private String epc;

    @Size(max = 100)
    private String tag;

    @Size(max = 100)
    private String tid;

    private Integer invTimes;
    private Integer rssi;
    private Integer antId;
    private Instant lastTime;
    private Instant firstUpdate;

    @Size(max = 30)
    private String color;

    @Size(max = 40)
    private String moduloId;

    @Size(max = 40)
    private String moduloRol;
}
