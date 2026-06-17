package com.pystelectronic.rfid.transfer.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request para abrir/asignar el traslado activo de un portal
 * antes de iniciar las lecturas RFID.
 *
 * Sprint 9 · Pystelectronic
 */
public record OpenPortalSessionRequest(

        @NotNull(message = "El transferId es obligatorio")
        UUID transferId
) {}
