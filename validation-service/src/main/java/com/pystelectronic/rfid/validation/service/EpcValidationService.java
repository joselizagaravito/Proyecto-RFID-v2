package com.pystelectronic.rfid.validation.service;

import com.pystelectronic.rfid.validation.dto.TransferStateEvent;
import com.pystelectronic.rfid.validation.dto.ValidatedReadEvent;
import com.pystelectronic.rfid.validation.entity.LpnView;
import com.pystelectronic.rfid.validation.entity.TransferView;
import com.pystelectronic.rfid.validation.repository.LpnQueryRepository;
import com.pystelectronic.rfid.validation.repository.TransferQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio central de validación del validation-service.
 *
 * Lógica de validación por EPC:
 *
 * 1. ¿El EPC existe en la tabla lpn?
 *    → NO  → UNREGISTERED_EPC   → publica en transfer.alerts
 *    → SÍ  → continúa
 *
 * 2. ¿El LPN pertenece a un traslado activo (DISPATCHED o IN_TRANSIT)?
 *    → NO  → EXTRA_LPN          → publica en transfer.alerts
 *    → SÍ  → continúa
 *
 * 3. EPC válido → VALID
 *    → Incrementa read_count en Redis (PortalStateService)
 *    → Publica en rfid.validated
 *    → Publica en transfer.events (para realtime-service y audit-service)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EpcValidationService {

    private static final String STATUS_VALID           = "VALID";
    private static final String STATUS_EXTRA_LPN       = "EXTRA_LPN";
    private static final String STATUS_UNREGISTERED    = "UNREGISTERED_EPC";

    private final LpnQueryRepository lpnRepository;
    private final TransferQueryRepository transferRepository;
    private final PortalStateService portalStateService;
    private final ValidationEventPublisher eventPublisher;

    /**
     * Punto de entrada principal. Valida el EPC y dispara los eventos correspondientes.
     *
     * @param epc        EPC leído por el portal/dispositivo
     * @param deviceId   ID del módulo lector
     * @param deviceRole Rol del módulo (GATE_OUT, GATE_IN, HANDHELD)
     * @param readAt     Timestamp de la lectura
     * @return Resultado de la validación
     */
    public ValidationResult validate(
            String epc, String deviceId, String deviceRole, Instant readAt) {

        String correlationId = UUID.randomUUID().toString();
        log.debug("Validando EPC={} desde deviceId={} correlationId={}", epc, deviceId, correlationId);

        // Paso 1: ¿El EPC existe en la tabla lpn?
        Optional<LpnView> lpnOpt = lpnRepository.findByEpc(epc);

        if (lpnOpt.isEmpty()) {
            log.warn("EPC no registrado: {} deviceId={}", epc, deviceId);
            publishAlert(STATUS_UNREGISTERED, epc, null, deviceId, deviceRole,
                    "EPC no existe en ningún LPN registrado", readAt, correlationId);
            return ValidationResult.unregistered(epc, correlationId);
        }

        LpnView lpn = lpnOpt.get();

        // Paso 2: ¿El LPN pertenece a un traslado activo?
        TransferView transfer = lpn.getPallet().getTransfer();
        boolean isActive = isTransferActive(transfer.getStatus());

        if (!isActive) {
            log.warn("EPC válido pero traslado no activo: EPC={} transferId={} status={}",
                    epc, transfer.getId(), transfer.getStatus());
            publishAlert(STATUS_EXTRA_LPN, epc, lpn.getLpnCode(), deviceId, deviceRole,
                    String.format("LPN %s pertenece a traslado en estado %s (no activo)",
                            lpn.getLpnCode(), transfer.getStatus()),
                    readAt, correlationId);
            return ValidationResult.extraLpn(epc, lpn.getLpnCode(), correlationId);
        }

        // Paso 3: EPC VÁLIDO
        long newReadCount = portalStateService.incrementReadCount(deviceId);

        // Actualizar estado del traslado activo en Redis
        int expectedLpns = lpnRepository.findByTransferId(transfer.getId()).size();
        portalStateService.updateTransferActiveState(
                transfer.getId().toString(),
                transfer.getTransferCode(),
                transfer.getStatus(),
                expectedLpns,
                newReadCount
        );

        // Publicar en rfid.validated
        ValidatedReadEvent validatedEvent = ValidatedReadEvent.builder()
                .epc(epc)
                .lpnCode(lpn.getLpnCode())
                .transferId(transfer.getId().toString())
                .transferCode(transfer.getTransferCode())
                .deviceId(deviceId)
                .deviceRole(deviceRole)
                .validationResult(STATUS_VALID)
                .reason("EPC validado correctamente")
                .readDateTime(readAt)
                .validatedAt(Instant.now())
                .correlationId(correlationId)
                .build();
        eventPublisher.publishValidated(validatedEvent);

        // Publicar en transfer.events
        TransferStateEvent stateEvent = TransferStateEvent.builder()
                .eventType("LPN_VALIDATED")
                .transferId(transfer.getId().toString())
                .transferCode(transfer.getTransferCode())
                .lpnCode(lpn.getLpnCode())
                .newStatus("VALIDATED")
                .readCount((int) newReadCount)
                .expectedCount(expectedLpns)
                .occurredAt(Instant.now())
                .correlationId(correlationId)
                .build();
        eventPublisher.publishTransferEvent(stateEvent);

        log.info("EPC válido: {} → LPN={} transferId={} readCount={}/{}",
                epc, lpn.getLpnCode(), transfer.getId(), newReadCount, expectedLpns);

        return ValidationResult.valid(epc, lpn.getLpnCode(),
                transfer.getId().toString(), (int) newReadCount, expectedLpns, correlationId);
    }

    private void publishAlert(
            String alertType, String epc, String lpnCode,
            String deviceId, String deviceRole,
            String reason, Instant readAt, String correlationId) {

        ValidatedReadEvent alertEvent = ValidatedReadEvent.builder()
                .epc(epc)
                .lpnCode(lpnCode)
                .deviceId(deviceId)
                .deviceRole(deviceRole)
                .validationResult(alertType)
                .reason(reason)
                .readDateTime(readAt)
                .validatedAt(Instant.now())
                .correlationId(correlationId)
                .build();
        eventPublisher.publishAlert(alertEvent);
    }

    private boolean isTransferActive(String status) {
        return "DISPATCHED".equals(status) || "IN_TRANSIT".equals(status);
    }

    // ────────────────────────────────────────────────
    // Result Value Object
    // ────────────────────────────────────────────────

    public record ValidationResult(
            String result,
            String epc,
            String lpnCode,
            String transferId,
            int readCount,
            int expectedCount,
            String correlationId
    ) {
        static ValidationResult valid(String epc, String lpnCode, String transferId,
                                      int readCount, int expectedCount, String correlationId) {
            return new ValidationResult(STATUS_VALID, epc, lpnCode, transferId,
                    readCount, expectedCount, correlationId);
        }

        static ValidationResult extraLpn(String epc, String lpnCode, String correlationId) {
            return new ValidationResult(STATUS_EXTRA_LPN, epc, lpnCode, null,
                    0, 0, correlationId);
        }

        static ValidationResult unregistered(String epc, String correlationId) {
            return new ValidationResult(STATUS_UNREGISTERED, epc, null, null,
                    0, 0, correlationId);
        }
    }
}
