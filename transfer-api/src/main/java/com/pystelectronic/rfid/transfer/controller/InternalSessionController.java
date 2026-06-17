package com.pystelectronic.rfid.transfer.controller;

import com.pystelectronic.rfid.transfer.controller.dto.RfidSessionReadRequest;
import com.pystelectronic.rfid.transfer.controller.dto.RfidSessionReadResponse;
import com.pystelectronic.rfid.transfer.service.PalletSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Controller INTERNO para ingesta de lecturas RFID desde validation-service.
 *
 * Ruta: /api/v1/internal/portals/{portalId}/session/read
 *
 * SEGURIDAD: este endpoint es permitAll en SecurityConfig pero NO se expone
 * vía Nginx. Solo es alcanzable dentro de la red Docker rfid-net por otros
 * servicios (validation-service). No debe enrutarse al exterior.
 *
 * Sprint 9 — Pystelectronic · Ing. José Hernán Liza Garavito
 */
@RestController
@RequestMapping("/api/v1/internal/portals/{portalId}/session")
public class InternalSessionController {

    private final PalletSessionService service;

    public InternalSessionController(PalletSessionService service) {
        this.service = service;
    }

    @PostMapping("/read")
    public RfidSessionReadResponse read(
            @PathVariable String portalId,
            @Valid @RequestBody RfidSessionReadRequest request) {
        return service.read(portalId, request.epc());
    }
}
