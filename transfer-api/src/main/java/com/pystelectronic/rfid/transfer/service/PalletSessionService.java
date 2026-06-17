package com.pystelectronic.rfid.transfer.service;

import com.pystelectronic.rfid.common.enums.LpnStatus;
import com.pystelectronic.rfid.common.enums.PalletStatus;
import com.pystelectronic.rfid.common.exception.RfidBusinessException;
import com.pystelectronic.rfid.common.exception.TransferNotFoundException;
import com.pystelectronic.rfid.transfer.controller.dto.PortalSessionResponse;
import com.pystelectronic.rfid.transfer.controller.dto.RfidSessionReadResponse;
import com.pystelectronic.rfid.transfer.entity.*;
import com.pystelectronic.rfid.transfer.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de sesión de pallet por portal (Sprint 9).
 *
 * Flujo:
 *   1. openSession(portalId, transferId) → asigna el traslado activo del portal
 *   2. read(portalId, epc):
 *        - Si el EPC está en pallet_tag → es PALLET → se abre/reactiva como activo
 *        - Si NO → es LPN:
 *            · hay pallet activo → se crea/recupera el LPN bajo ese pallet
 *            · no hay pallet activo → LPN_REJECTED
 *
 * Toda la operación es transaccional (ACID) — crear pallet/LPN y actualizar
 * la sesión ocurren en un único commit.
 *
 * Pystelectronic · Ing. José Hernán Liza Garavito
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PalletSessionService {

    private final PortalSessionRepository   sessionRepository;
    private final TransferRepository        transferRepository;
    private final PalletRepository          palletRepository;
    private final LpnRepository             lpnRepository;
    private final PalletTagRepository       palletTagRepository;
    private final CodeGeneratorService      codeGenerator;
    private final PalletSessionEventPublisher eventPublisher;

    /**
     * Abre (o reasigna) la sesión de un portal con un traslado activo.
     * Resetea el pallet activo: el operador empieza de cero con este traslado.
     */
    @Transactional
    public PortalSessionResponse openSession(String portalId, UUID transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId.toString()));

        PortalSession session = sessionRepository.findByPortalIdForUpdate(portalId)
                .orElseGet(() -> PortalSession.builder().portalId(portalId).build());

        session.setTransferId(transferId);
        session.setActivePalletId(null);   // nuevo traslado → sin pallet activo
        session.setLpnCount(0);
        session.setOpenedAt(OffsetDateTime.now());
        session.touch();
        sessionRepository.save(session);

        log.info("Sesión de portal {} abierta con traslado {} ({})",
                portalId, transfer.getTransferCode(), transferId);

        return toResponse(session, transfer.getTransferCode(), null);
    }

    /**
     * Procesa una lectura RFID en el portal según el tipo del EPC.
     */
    @Transactional
    public RfidSessionReadResponse read(String portalId, String epc) {
        PortalSession session = sessionRepository.findByPortalIdForUpdate(portalId)
                .orElseThrow(() -> new RfidBusinessException(
                        "NO_SESSION",
                        "No hay sesión abierta en el portal " + portalId
                                + ". Selecciona un traslado antes de leer.",
                        HttpStatus.CONFLICT));

        if (session.getTransferId() == null) {
            throw new RfidBusinessException(
                    "NO_TRANSFER_SELECTED",
                    "El portal " + portalId + " no tiene traslado activo.",
                    HttpStatus.CONFLICT);
        }

        boolean esPallet = palletTagRepository.existsByEpc(epc);
        return esPallet
                ? procesarPallet(session, epc)
                : procesarLpn(session, epc);
    }

    // ── PALLET ────────────────────────────────────────────────
    private RfidSessionReadResponse procesarPallet(PortalSession session, String epc) {
        Transfer transfer = transferRepository.findById(session.getTransferId())
                .orElseThrow(() -> new TransferNotFoundException(
                        session.getTransferId().toString()));

        // Dedup: ¿ya existe un pallet con este EPC en este traslado?
        Optional<Pallet> existente = palletRepository
                .findByEpcAndTransferId(epc, transfer.getId());

        Pallet pallet;
        boolean reused;
        if (existente.isPresent()) {
            pallet = existente.get();
            reused = true;
            log.info("Pallet reutilizado epc={} code={}", epc, pallet.getPalletCode());
        } else {
            String palletCode = codeGenerator.nextPalletCode();
            pallet = Pallet.builder()
                    .palletCode(palletCode)
                    .epc(epc)
                    .transfer(transfer)
                    .status(PalletStatus.BUILDING)
                    .build();
            pallet = palletRepository.save(pallet);
            reused = false;
            log.info("Pallet creado code={} epc={} transfer={}",
                    palletCode, epc, transfer.getTransferCode());
        }

        // Marcar como pallet activo de la sesión
        session.setActivePalletId(pallet.getId());
        session.setLpnCount(pallet.getTotalLpns() != null ? pallet.getTotalLpns() : 0);
        session.touch();
        sessionRepository.save(session);

        RfidSessionReadResponse resp = RfidSessionReadResponse.palletOpened(
                pallet.getId(), pallet.getPalletCode(), epc,
                transfer.getId(), session.getPortalId(), reused);
        eventPublisher.publishPalletOpened(resp);
        return resp;
    }

    // ── LPN ───────────────────────────────────────────────────
    private RfidSessionReadResponse procesarLpn(PortalSession session, String epc) {
        // Sin pallet activo → rechazo
        if (session.getActivePalletId() == null) {
            log.warn("LPN rechazado epc={} portal={} — sin pallet activo",
                    epc, session.getPortalId());
            RfidSessionReadResponse resp =
                    RfidSessionReadResponse.lpnRejected(epc, session.getPortalId());
            eventPublisher.publishLpnRejected(resp);
            return resp;
        }

        Pallet pallet = palletRepository.findById(session.getActivePalletId())
                .orElseThrow(() -> new RfidBusinessException(
                        "ACTIVE_PALLET_MISSING",
                        "El pallet activo de la sesión ya no existe.",
                        HttpStatus.CONFLICT));

        UUID transferId = session.getTransferId();

        // Dedup: ¿ya existe un LPN con este EPC en este traslado?
        Optional<Lpn> existente = lpnRepository.findByEpcAndTransferId(epc, transferId);
        if (existente.isPresent()) {
            Lpn lpn = existente.get();
            log.info("LPN reutilizado epc={} code={}", epc, lpn.getLpnCode());
            RfidSessionReadResponse resp = RfidSessionReadResponse.lpnAdded(
                    pallet.getId(), pallet.getPalletCode(),
                    lpn.getId(), lpn.getLpnCode(), epc,
                    transferId, session.getPortalId(),
                    session.getLpnCount() != null ? session.getLpnCount() : 0, true);
            eventPublisher.publishLpnAdded(resp);
            return resp;
        }

        // Crear LPN nuevo bajo el pallet activo
        String lpnCode = codeGenerator.nextLpnCode();
        Lpn lpn = Lpn.builder()
                .lpnCode(lpnCode)
                .epc(epc)
                .pallet(pallet)
                .transfer(pallet.getTransfer())
                .status(LpnStatus.REGISTERED)
                .build();
        lpn = lpnRepository.save(lpn);

        // Actualizar totales del pallet
        pallet.setTotalLpns((pallet.getTotalLpns() != null ? pallet.getTotalLpns() : 0) + 1);
        palletRepository.save(pallet);

        // Actualizar sesión
        session.incrementLpn();
        sessionRepository.save(session);

        log.info("LPN creado code={} epc={} pallet={}",
                lpnCode, epc, pallet.getPalletCode());

        RfidSessionReadResponse resp = RfidSessionReadResponse.lpnAdded(
                pallet.getId(), pallet.getPalletCode(),
                lpn.getId(), lpnCode, epc,
                transferId, session.getPortalId(),
                session.getLpnCount(), false);
        eventPublisher.publishLpnAdded(resp);
        return resp;
    }

    // ── Consulta de sesión ────────────────────────────────────
    @Transactional(readOnly = true)
    public PortalSessionResponse getSession(String portalId) {
        PortalSession session = sessionRepository.findById(portalId)
                .orElseThrow(() -> new RfidBusinessException(
                        "NO_SESSION",
                        "No hay sesión para el portal " + portalId,
                        HttpStatus.NOT_FOUND));

        String transferCode = null;
        if (session.getTransferId() != null) {
            transferCode = transferRepository.findById(session.getTransferId())
                    .map(Transfer::getTransferCode).orElse(null);
        }
        String palletCode = null;
        if (session.getActivePalletId() != null) {
            palletCode = palletRepository.findById(session.getActivePalletId())
                    .map(Pallet::getPalletCode).orElse(null);
        }
        return toResponse(session, transferCode, palletCode);
    }

    /**
     * Cierra manualmente la sesión de pallet (sin borrar el traslado activo).
     * El siguiente LPN será rechazado hasta que se lea un nuevo pallet.
     */
    @Transactional
    public void closePallet(String portalId) {
        sessionRepository.findByPortalIdForUpdate(portalId).ifPresent(s -> {
            s.setActivePalletId(null);
            s.touch();
            sessionRepository.save(s);
            log.info("Pallet activo cerrado en portal {}", portalId);
        });
    }

    private PortalSessionResponse toResponse(PortalSession s, String transferCode, String palletCode) {
        return new PortalSessionResponse(
                s.getPortalId(), s.getTransferId(), transferCode,
                s.getActivePalletId(), palletCode,
                s.getLpnCount(), s.getOpenedAt(), s.getLastReadAt());
    }
}
