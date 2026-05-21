package com.pystelectronic.rfid.transfer.service.impl;

import com.pystelectronic.rfid.common.dto.request.*;
import com.pystelectronic.rfid.common.dto.response.*;
import com.pystelectronic.rfid.common.enums.*;
import com.pystelectronic.rfid.common.exception.*;
import com.pystelectronic.rfid.transfer.entity.*;
import com.pystelectronic.rfid.transfer.mapper.TransferMapper;
import com.pystelectronic.rfid.transfer.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl {

    private final TransferRepository transferRepository;
    private final PalletRepository palletRepository;
    private final LpnRepository lpnRepository;
    private final TransferSequenceRepository sequenceRepository;
    private final TransferMapper transferMapper;

    public TransferResponse createTransfer(CreateTransferRequest req, String userId, String idempotencyKey) {
        log.info("Creando traslado: origen={} destino={} usuario={}", req.getOriginCode(), req.getDestinationCode(), userId);

        if (req.getOriginCode().equalsIgnoreCase(req.getDestinationCode())) {
            throw new RfidBusinessException("SAME_ORIGIN_DESTINATION",
                "El origen y el destino no pueden ser el mismo codigo",
                org.springframework.http.HttpStatus.BAD_REQUEST);
        }

        if (idempotencyKey != null) {
            transferRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
                throw new IdempotencyConflictException(idempotencyKey);
            });
        }

        String transferCode = generateTransferCode();

        Transfer transfer = Transfer.builder()
                .transferCode(transferCode)
                .originCode(req.getOriginCode().toUpperCase())
                .destinationCode(req.getDestinationCode().toUpperCase())
                .status(TransferStatus.DRAFT)
                .priority(req.getPriority())
                .carrierId(req.getCarrierId())
                .scheduledDate(req.getScheduledDate())
                .remarks(req.getRemarks())
                .idempotencyKey(idempotencyKey)
                .createdBy(userId != null ? userId : "system")
                .build();

        transfer = transferRepository.save(transfer);
        log.info("Traslado creado: {} codigo: {}", transfer.getId(), transferCode);
        return transferMapper.toResponse(transfer);
    }

    public PalletResponse addPallet(UUID transferId, CreatePalletRequest req) {
        Transfer transfer = findTransferOrThrow(transferId);
        if (!transfer.isEditable()) {
            throw new InvalidTransferStateException("No se puede agregar pallets en estado " + transfer.getStatus());
        }
        if (palletRepository.existsByPalletCodeAndTransferId(req.getPalletCode(), transferId)) {
            throw new RfidBusinessException("DUPLICATE_PALLET_IN_TRANSFER",
                "El pallet " + req.getPalletCode() + " ya existe en este traslado",
                org.springframework.http.HttpStatus.CONFLICT);
        }
        Pallet pallet = Pallet.builder()
                .palletCode(req.getPalletCode())
                .transfer(transfer)
                .grossWeight(req.getGrossWeight())
                .heightCm(req.getHeightCm())
                .widthCm(req.getWidthCm())
                .lengthCm(req.getLengthCm())
                .remarks(req.getRemarks())
                .status(PalletStatus.CREATED)
                .build();
        pallet = palletRepository.save(pallet);
        transfer.setTotalPallets(transfer.getTotalPallets() + 1);
        if (transfer.getStatus() == TransferStatus.DRAFT) {
            transfer.setStatus(TransferStatus.PREPARED);
        }
        transferRepository.save(transfer);
        return transferMapper.toPalletResponse(pallet);
    }

    public ContentItemResponse addPalletContent(UUID palletId, AddPalletContentRequest req) {
        Pallet pallet = palletRepository.findById(palletId)
                .orElseThrow(() -> new PalletNotFoundException(palletId.toString()));
        Transfer transfer = pallet.getTransfer();
        if (!transfer.isEditable()) {
            throw new InvalidTransferStateException("El traslado no acepta modificaciones en estado " + transfer.getStatus());
        }
        if (req.getContentType() == ContentType.LPN) {
            return addLpnContent(pallet, req);
        } else {
            return addLooseItemContent(pallet, req);
        }
    }

    private ContentItemResponse addLpnContent(Pallet pallet, AddPalletContentRequest req) {
        if (req.getLpnCode() == null) {
            throw new RfidBusinessException("LPN_CODE_REQUIRED", "lpnCode es requerido para contentType=LPN",
                org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (lpnRepository.existsByLpnCode(req.getLpnCode())) {
            throw new DuplicateLpnException(req.getLpnCode());
        }
        Lpn lpn = Lpn.builder()
                .lpnCode(req.getLpnCode())
                .epc(req.getEpc())
                .pallet(pallet)
                .transfer(pallet.getTransfer())
                .isKit(Boolean.TRUE.equals(req.getIsKit()))
                .piecesInside(req.getPiecesInside() != null ? req.getPiecesInside() : 0)
                .status(LpnStatus.REGISTERED)
                .build();
        List<LpnSku> skus = req.getSkus().stream().map(s -> LpnSku.builder()
                .lpn(lpn).skuCode(s.getSkuCode())
                .skuDescription(s.getSkuDescription())
                .unitQuantity(s.getUnitQuantity()).build()).toList();
        lpn.getSkus().addAll(skus);
        lpn.recalculateTotalUnits();
        lpnRepository.save(lpn);
        pallet.setTotalLpns(pallet.getTotalLpns() + 1);
        pallet.setTotalUnits(pallet.getTotalUnits() + lpn.getTotalUnits());
        palletRepository.save(pallet);
        updateTransferTotals(pallet.getTransfer());
        return transferMapper.toContentItemResponse(lpn);
    }

    private ContentItemResponse addLooseItemContent(Pallet pallet, AddPalletContentRequest req) {
        AddPalletContentRequest.SkuEntry sku = req.getSkus().get(0);
        LooseItem item = LooseItem.builder()
                .pallet(pallet).transfer(pallet.getTransfer())
                .skuCode(sku.getSkuCode()).skuDescription(sku.getSkuDescription())
                .unitQuantity(sku.getUnitQuantity()).status("REGISTERED").build();
        pallet.getLooseItems().add(item);
        pallet.setTotalLooseItems(pallet.getTotalLooseItems() + 1);
        pallet.setTotalUnits(pallet.getTotalUnits() + item.getUnitQuantity());
        palletRepository.save(pallet);
        updateTransferTotals(pallet.getTransfer());
        return transferMapper.toContentItemResponseFromLooseItem(item);
    }

    public RfidValidationResponse validateRfid(UUID transferId, ValidateRfidRequest req) {
        Transfer transfer = findTransferOrThrow(transferId);
        Lpn lpn = null;
        if (req.getLpnCode() != null) {
            lpn = lpnRepository.findByLpnCodeAndTransferId(req.getLpnCode(), transferId).orElse(null);
        } else if (req.getEpc() != null) {
            lpn = lpnRepository.findByEpcAndTransferId(req.getEpc(), transferId).orElse(null);
        }
        if (lpn == null) {
            return RfidValidationResponse.builder()
                    .result("INVALID").reason("El LPN o EPC no pertenece a este traslado").build();
        }
        lpn.setStatus(LpnStatus.VALIDATED);
        lpn.setUpdatedAt(OffsetDateTime.now());
        lpnRepository.save(lpn);
        return RfidValidationResponse.builder()
                .result("VALID").reason("LPN validado correctamente")
                .lpnId(lpn.getId().toString()).lpnStatus(lpn.getStatus()).build();
    }

    public DispatchResponse dispatchTransfer(UUID transferId, DispatchTransferRequest req, String idempotencyKey) {
        Transfer transfer = findTransferOrThrow(transferId);
        if (transfer.getStatus() != TransferStatus.PREPARED && transfer.getStatus() != TransferStatus.DRAFT) {
            throw new InvalidTransferStateException("Solo se puede despachar un traslado en estado PREPARED o DRAFT");
        }
        transfer.setStatus(TransferStatus.DISPATCHED);
        transfer.setDispatchedAt(req.getDispatchDateTime());
        transfer.setVehiclePlate(req.getVehiclePlate());
        transfer.setShippingNote(req.getShippingNote());
        transferRepository.save(transfer);
        return DispatchResponse.builder()
                .transferId(transferId.toString()).status(TransferStatus.DISPATCHED)
                .dispatchedLpnCount(req.getLpnList().size())
                .dispatchedLooseItemCount(req.getLooseItemsList() != null ? req.getLooseItemsList().size() : 0)
                .dispatchedTotalUnits(transfer.getTotalUnits())
                .dispatchDateTime(req.getDispatchDateTime()).build();
    }

    public ReceiptResponse registerReceipt(UUID transferId, RegisterReceiptRequest req) {
        Transfer transfer = findTransferOrThrow(transferId);
        List<Lpn> dispatchedLpns = lpnRepository.findByTransferId(transferId).stream()
                .filter(l -> l.getStatus() == LpnStatus.DISPATCHED || l.getStatus() == LpnStatus.IN_TRANSIT)
                .toList();
        List<String> readLpnCodes = req.getReadings().stream()
                .filter(r -> r.getLpnCode() != null)
                .map(RegisterReceiptRequest.ReadingEntry::getLpnCode).toList();
        List<ReceiptResponse.IncidentEntry> incidents = new ArrayList<>();
        int receivedLpns = 0;
        for (Lpn lpn : dispatchedLpns) {
            if (readLpnCodes.contains(lpn.getLpnCode())) {
                lpn.setStatus(LpnStatus.RECEIVED);
                lpn.setUpdatedAt(OffsetDateTime.now());
                receivedLpns++;
            } else {
                lpn.setStatus(LpnStatus.MISSING);
                lpn.setUpdatedAt(OffsetDateTime.now());
                incidents.add(ReceiptResponse.IncidentEntry.builder()
                        .type(IncidentType.MISSING).lpnCode(lpn.getLpnCode())
                        .details("LPN no escaneado en recepcion").build());
            }
        }
        transfer.setStatus(incidents.isEmpty() ? TransferStatus.RECEIVED : TransferStatus.PARTIALLY_RECEIVED);
        transfer.setReceivedAt(req.getReceiptDateTime());
        transferRepository.save(transfer);
        return ReceiptResponse.builder()
                .receiptId(UUID.randomUUID().toString())
                .receiptStatus(incidents.isEmpty() ? "RECEIVED" : "PARTIALLY_RECEIVED")
                .expectedLpns(dispatchedLpns.size()).receivedLpns(receivedLpns)
                .incidents(incidents).build();
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransferById(UUID transferId) {
        Transfer transfer = transferRepository.findByIdWithPallets(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId.toString()));
        return transferMapper.toDetailedResponse(transfer);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> listTransfers(
            TransferStatus status, String originCode, String destinationCode,
            OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable) {

        Page<Transfer> page = transferRepository.findAll(pageable);

        return PageResponse.<TransferResponse>builder()
                .content(page.getContent().stream().map(transferMapper::toResponse).toList())
                .totalElements(page.getTotalElements())
                .page(page.getNumber()).size(page.getSize())
                .totalPages(page.getTotalPages()).last(page.isLast()).build();
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse getReconciliation(UUID transferId) {
        Transfer transfer = findTransferOrThrow(transferId);
        List<Lpn> lpns = lpnRepository.findByTransferId(transferId);
        int received = (int) lpns.stream().filter(l -> l.getStatus() == LpnStatus.RECEIVED).count();
        int dispatched = (int) lpns.stream().filter(l ->
                l.getStatus() == LpnStatus.DISPATCHED || l.getStatus() == LpnStatus.RECEIVED ||
                l.getStatus() == LpnStatus.MISSING).count();
        List<ReceiptResponse.IncidentEntry> incidents = lpns.stream()
                .filter(l -> l.getStatus() == LpnStatus.MISSING)
                .map(l -> ReceiptResponse.IncidentEntry.builder()
                        .type(IncidentType.MISSING).lpnCode(l.getLpnCode())
                        .unitQuantity(l.getTotalUnits()).details("LPN no recibido").build())
                .toList();
        return ReconciliationResponse.builder()
                .transferId(transferId.toString())
                .totalDispatched(dispatched).totalReceived(received)
                .lpnDifference(received - dispatched)
                .incidents(incidents)
                .result(incidents.isEmpty() ? "COMPLIANT" : "FLAGGED").build();
    }

    private Transfer findTransferOrThrow(UUID transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId.toString()));
    }

    private void updateTransferTotals(Transfer transfer) {
        List<Pallet> pallets = palletRepository.findByTransferId(transfer.getId());
        transfer.setTotalLpns(pallets.stream().mapToInt(p -> p.getTotalLpns() != null ? p.getTotalLpns() : 0).sum());
        transfer.setTotalLooseItems(pallets.stream().mapToInt(p -> p.getTotalLooseItems() != null ? p.getTotalLooseItems() : 0).sum());
        transfer.setTotalUnits(pallets.stream().mapToInt(p -> p.getTotalUnits() != null ? p.getTotalUnits() : 0).sum());
        transferRepository.save(transfer);
    }

    private String generateTransferCode() {
        String dateKey = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        TransferSequence seq = sequenceRepository.findByDateKeyForUpdate(dateKey)
                .orElseGet(() -> sequenceRepository.save(
                    TransferSequence.builder().dateKey(dateKey).build()));
        long number = seq.getAndIncrement();
        sequenceRepository.save(seq);
        return String.format("OT-%s-%06d", dateKey, number);
    }
}