package com.pystelectronic.rfid.transfer.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Estado actual de la sesión de un portal.
 *
 * Sprint 9 · Pystelectronic
 */
public record PortalSessionResponse(
        String         portalId,
        UUID           transferId,
        String         transferCode,
        UUID           activePalletId,
        String         activePalletCode,
        Integer        lpnCount,
        OffsetDateTime openedAt,
        OffsetDateTime lastReadAt
) {}
