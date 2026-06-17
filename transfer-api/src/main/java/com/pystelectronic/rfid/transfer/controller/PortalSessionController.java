package com.pystelectronic.rfid.transfer.controller;

import com.pystelectronic.rfid.transfer.controller.dto.OpenPortalSessionRequest;
import com.pystelectronic.rfid.transfer.controller.dto.PortalSessionResponse;
import com.pystelectronic.rfid.transfer.controller.dto.RfidSessionReadRequest;
import com.pystelectronic.rfid.transfer.controller.dto.RfidSessionReadResponse;
import com.pystelectronic.rfid.transfer.service.PalletSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller para sesiones de pallet por portal RFID.
 *
 * Flujo operativo:
 *   1. POST /api/v1/portals/{portalId}/session/open  { transferId }
 *        → el operador asigna el traslado activo del portal
 *   2. POST /api/v1/portals/{portalId}/session/read  { epc }
 *        → cada lectura: abre pallet o asocia LPN según el tipo del EPC
 *   3. GET  /api/v1/portals/{portalId}/session
 *        → estado actual (pallet activo, contador de LPNs)
 *   4. POST /api/v1/portals/{portalId}/session/close-pallet
 *        → cierra el pallet activo (los LPN siguientes se rechazan)
 *
 * Seguridad: perfil dev permitAll; perfil prod aplica RBAC.
 *
 * Sprint 9 — Pystelectronic · Ing. José Hernán Liza Garavito
 */
@RestController
@RequestMapping("/api/v1/portals/{portalId}/session")
public class PortalSessionController {

    private final PalletSessionService service;

    public PortalSessionController(PalletSessionService service) {
        this.service = service;
    }

    /** Abre/asigna el traslado activo del portal. */
    @PostMapping("/open")
    public PortalSessionResponse open(
            @PathVariable String portalId,
            @Valid @RequestBody OpenPortalSessionRequest request) {
        return service.openSession(portalId, request.transferId());
    }

    /** Procesa una lectura RFID (pallet o LPN). */
    @PostMapping("/read")
    public RfidSessionReadResponse read(
            @PathVariable String portalId,
            @Valid @RequestBody RfidSessionReadRequest request) {
        return service.read(portalId, request.epc());
    }

    /** Estado actual de la sesión. */
    @GetMapping
    public PortalSessionResponse getSession(@PathVariable String portalId) {
        return service.getSession(portalId);
    }

    /** Cierra el pallet activo (sin cerrar el traslado). */
    @PostMapping("/close-pallet")
    public ResponseEntity<Void> closePallet(@PathVariable String portalId) {
        service.closePallet(portalId);
        return ResponseEntity.noContent().build();
    }
}
