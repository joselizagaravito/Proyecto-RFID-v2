package com.pystelectronic.rfid.transfer.controller.dto;

import jakarta.validation.constraints.*;

/**
 * DTO de entrada para crear un pallet tag.
 * POST /api/v1/pallet-tags
 *
 * Sprint 8 — Pystelectronic
 */
public record PalletTagRequest(

    @NotBlank(message = "El EPC es obligatorio")
    @Size(max = 50, message = "El EPC no puede superar 50 caracteres")
    @Pattern(
        regexp = "^[0-9A-Fa-f]{10,50}$",
        message = "El EPC debe ser hexadecimal sin guiones (10-50 caracteres)"
    )
    String epc,

    @Size(max = 50, message = "El TID no puede superar 50 caracteres")
    String tid,

    @Size(max = 100, message = "La descripción no puede superar 100 caracteres")
    String descripcion
) {}
