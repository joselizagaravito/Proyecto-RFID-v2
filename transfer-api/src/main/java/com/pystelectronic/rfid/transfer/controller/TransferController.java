package com.pystelectronic.rfid.transfer.controller;

import com.pystelectronic.rfid.common.dto.request.*;
import com.pystelectronic.rfid.common.dto.response.*;
import com.pystelectronic.rfid.common.enums.TransferStatus;
import com.pystelectronic.rfid.transfer.service.impl.TransferServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Controlador REST principal del sistema de traslados RFID.
 * Prefijo base: /api/v1
 * Spec §6.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transfers", description = "Gestión de traslados RFID")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferServiceImpl transferService;

    // ─────────────────────────────────────────────────────────────────
    // §6.1 POST /transfers — Crear traslado
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Crear traslado", description = "Crea un traslado en estado DRAFT. Requiere X-Idempotency-Key.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Traslado creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "Clave de idempotencia ya procesada o mismo origen/destino")
    })
    public ResponseEntity<TransferResponse> createTransfer(
        @Valid @RequestBody CreateTransferRequest request,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

       String userId = "system";
        TransferResponse response = transferService.createTransfer(request, userId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.6 GET /transfers — Listar traslados (paginado)
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/transfers")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','READER','DEVICE')")
    @Operation(summary = "Listar traslados", description = "Devuelve lista paginada con filtros opcionales.")
    public ResponseEntity<PageResponse<TransferResponse>> listTransfers(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) String originCode,
            @RequestParam(required = false) String destinationCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        // Limitar tamaño máximo de página
        int safeSize = Math.min(size, 100);
        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, safeSize, Sort.by(direction, sortParts[0]));

        return ResponseEntity.ok(transferService.listTransfers(
                status, originCode, destinationCode, startDate, endDate, pageable));
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.5 GET /transfers/{transferId} — Vista detallada
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/transfers/{transferId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','READER','DEVICE')")
    @Operation(summary = "Obtener traslado detallado", description = "Devuelve estructura jerárquica completa con pallets y contenido.")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable UUID transferId) {
        return ResponseEntity.ok(transferService.getTransferById(transferId));
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.2 POST /transfers/{transferId}/pallets — Agregar pallet
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/transfers/{transferId}/pallets")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Agregar pallet", description = "Agrega un pallet al traslado. Solo en estados DRAFT o PREPARED.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pallet agregado"),
            @ApiResponse(responseCode = "409", description = "Código de pallet duplicado en el traslado")
    })
    public ResponseEntity<PalletResponse> addPallet(
            @PathVariable UUID transferId,
            @Valid @RequestBody CreatePalletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.addPallet(transferId, request));
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.3 POST /pallets/{palletId}/contents — Agregar contenido
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/pallets/{palletId}/contents")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Agregar contenido a pallet", description = "Registra un LPN o ítem suelto dentro de un pallet.")
    public ResponseEntity<ContentItemResponse> addPalletContent(
            @PathVariable UUID palletId,
            @Valid @RequestBody AddPalletContentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.addPalletContent(palletId, request));
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.4 GET /pallets/{palletId} — Obtener pallet con contenido
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/pallets/{palletId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','READER','DEVICE')")
    @Operation(summary = "Obtener pallet con contenido")
    public ResponseEntity<PalletResponse> getPallet(@PathVariable UUID palletId) {
        // Delegado al servicio de pallets (simplificado aquí para el ejemplo)
        throw new UnsupportedOperationException("Implementar en sprint 2 — PalletServiceImpl");
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.7 POST /transfers/{transferId}/rfid-validations — Validar RFID
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/transfers/{transferId}/rfid-validations")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','DEVICE')")
    @Operation(summary = "Validar lectura RFID", description = "Registra y valida la lectura RFID de un LPN previo al despacho.")
    public ResponseEntity<RfidValidationResponse> validateRfid(
            @PathVariable UUID transferId,
            @Valid @RequestBody ValidateRfidRequest request) {
        return ResponseEntity.ok(transferService.validateRfid(transferId, request));
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.8 POST /transfers/{transferId}/dispatch — Confirmar despacho
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/transfers/{transferId}/dispatch")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Confirmar despacho", description = "Confirma la salida del traslado. Requiere X-Idempotency-Key.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Traslado despachado"),
            @ApiResponse(responseCode = "409", description = "Traslado ya despachado o unidades no coinciden")
    })
    public ResponseEntity<DispatchResponse> dispatchTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody DispatchTransferRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(transferService.dispatchTransfer(transferId, request, idempotencyKey));
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.9 POST /transfers/{transferId}/receipts — Registrar recepción
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/transfers/{transferId}/receipts")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Registrar recepción", description = "Registra la recepción en tienda destino y reconcilia contra el despacho.")
    public ResponseEntity<ReceiptResponse> registerReceipt(
            @PathVariable UUID transferId,
            @Valid @RequestBody RegisterReceiptRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.registerReceipt(transferId, request));
    }

    // ─────────────────────────────────────────────────────────────────
    // §6.10 GET /transfers/{transferId}/reconciliation — Conciliación
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/transfers/{transferId}/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','READER')")
    @Operation(summary = "Consultar conciliación", description = "Retorna la comparación entre lo despachado y lo recibido.")
    public ResponseEntity<ReconciliationResponse> getReconciliation(
            @PathVariable UUID transferId) {
        return ResponseEntity.ok(transferService.getReconciliation(transferId));
    }

    // ─────────────────────────────────────────────────────────────────
    // Health check sin autenticación (Actuator ya lo expone, pero útil
    // para el frontend en la prueba de conexión)
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    @Operation(hidden = true)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\",\"service\":\"transfer-api\"}");
    }
}
