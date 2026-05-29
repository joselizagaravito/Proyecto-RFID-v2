package com.pystelectronic.rfid.transfer.controller;

import com.pystelectronic.rfid.transfer.controller.dto.PalletTagRequest;
import com.pystelectronic.rfid.transfer.controller.dto.PalletTagResponse;
import com.pystelectronic.rfid.transfer.service.PalletTagService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * REST Controller para pallet tags.
 *
 * Endpoints:
 *   GET    /api/v1/pallet-tags              → listar (paginado, filtro ?q=, ?soloActivos=)
 *   GET    /api/v1/pallet-tags/{id}         → obtener por ID
 *   POST   /api/v1/pallet-tags              → crear  (OPERATOR, ADMIN)
 *   PATCH  /api/v1/pallet-tags/{id}/desactivar → soft-delete (OPERATOR, ADMIN)
 *   DELETE /api/v1/pallet-tags/{id}         → eliminar físico (ADMIN)
 *
 * Seguridad: en perfil dev DevSecurityConfig permite todo (permitAll).
 *            En perfil prod SecurityConfig aplica RBAC por rol.
 *
 * Sprint 8 — Pystelectronic · Ing. José Hernán Liza Garavito
 */
@RestController
@RequestMapping("/api/v1/pallet-tags")
public class PalletTagController {

    private final PalletTagService service;

    public PalletTagController(PalletTagService service) {
        this.service = service;
    }

    /**
     * GET /api/v1/pallet-tags
     *
     * Query params opcionales:
     *   q            → filtro texto (EPC o descripción)
     *   soloActivos  → true (default) | false
     *   page, size, sort → Pageable estándar Spring
     */
    @GetMapping
    public Page<PalletTagResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return service.listar(q, soloActivos, pageable);
    }

    /** GET /api/v1/pallet-tags/{id} */
    @GetMapping("/{id}")
    public PalletTagResponse obtener(@PathVariable UUID id) {
        return service.obtener(id);
    }

    /**
     * POST /api/v1/pallet-tags
     * Devuelve 201 Created con Location apuntando al nuevo recurso.
     */
    @PostMapping
    public ResponseEntity<PalletTagResponse> crear(
            @Valid @RequestBody PalletTagRequest request,
            Authentication auth) {

        String usuario = (auth != null) ? auth.getName() : "system";
        PalletTagResponse response = service.crear(request, usuario);

        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * PATCH /api/v1/pallet-tags/{id}/desactivar
     * Soft-delete: marca activo=false, el registro queda en BD para auditoría.
     */
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/pallet-tags/{id}
     * Eliminación física. Solo ADMIN debe tener acceso.
     * En SecurityConfig (perfil prod) agregar: .hasRole("ADMIN") para este path.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
