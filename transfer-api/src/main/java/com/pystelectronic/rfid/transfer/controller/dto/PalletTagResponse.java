package com.pystelectronic.rfid.transfer.controller.dto;

import com.pystelectronic.rfid.transfer.model.PalletTag;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida para pallet tag.
 * Sprint 8 — Pystelectronic
 */
public record PalletTagResponse(
    UUID    id,
    String  epc,
    String  tid,
    String  descripcion,
    boolean activo,
    String  createdBy,
    Instant createdAt,
    Instant updatedAt
) {
    /** Convierte la entidad JPA al DTO de respuesta. */
    public static PalletTagResponse from(PalletTag pt) {
        return new PalletTagResponse(
            pt.getId(),
            pt.getEpc(),
            pt.getTid(),
            pt.getDescripcion(),
            pt.isActivo(),
            pt.getCreatedBy(),
            pt.getCreatedAt(),
            pt.getUpdatedAt()
        );
    }
}
