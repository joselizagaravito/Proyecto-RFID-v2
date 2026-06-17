package com.pystelectronic.rfid.transfer.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request para una lectura RFID en sesión de pallet.
 * El EPC es el identificador físico del tag (36 chars alfanuméricos).
 *
 * Sprint 9 · Pystelectronic
 */
public record RfidSessionReadRequest(

        @NotBlank(message = "El EPC es obligatorio")
        @Pattern(regexp = "^[0-9A-Za-z]{1,36}$",
                 message = "EPC debe ser alfanumérico de hasta 36 caracteres")
        String epc,

        // Opcional: el cliente puede forzar el tipo. Si es null, se infiere desde pallet_tag.
        String tipoHint
) {}
