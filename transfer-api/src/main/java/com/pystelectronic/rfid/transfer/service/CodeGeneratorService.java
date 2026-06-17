package com.pystelectronic.rfid.transfer.service;

import com.pystelectronic.rfid.transfer.entity.PalletLpnSequence;
import com.pystelectronic.rfid.transfer.repository.PalletLpnSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Genera códigos de negocio para pallets y LPNs con secuencia diaria.
 *
 *   pallet_code = "PL" + YYMMDD + NNNNNN   (PL + 12 dígitos)  → ^PL[0-9]{12}$
 *   lpn_code    =        YYMMDD + NNNNNNNN  (14 dígitos)       → ^[0-9]{14}$
 *
 * Usa bloqueo pesimista por clave (tipo+fecha) para evitar colisiones
 * de correlativos en lecturas concurrentes.
 *
 * Sprint 9 · Pystelectronic
 */
@Service
@RequiredArgsConstructor
public class CodeGeneratorService {

    private static final DateTimeFormatter YYMMDD =
            DateTimeFormatter.ofPattern("yyMMdd");

    private final PalletLpnSequenceRepository sequenceRepository;

    /**
     * Genera el siguiente pallet_code: PL + YYMMDD + 6 dígitos correlativos.
     * Debe ejecutarse dentro de la transacción del llamador (REQUIRED) para
     * que el bloqueo pesimista persista hasta el commit.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String nextPalletCode() {
        String dateKey = LocalDate.now().format(YYMMDD);     // 6 chars
        long number = nextSequence("PL-" + dateKey);
        // PL + 6 (fecha) + 6 (correlativo) = PL + 12 dígitos
        return String.format("PL%s%06d", dateKey, number);
    }

    /**
     * Genera el siguiente lpn_code: YYMMDD + 8 dígitos correlativos.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String nextLpnCode() {
        String dateKey = LocalDate.now().format(YYMMDD);     // 6 chars
        long number = nextSequence("LPN-" + dateKey);
        // 6 (fecha) + 8 (correlativo) = 14 dígitos
        return String.format("%s%08d", dateKey, number);
    }

    private long nextSequence(String seqKey) {
        PalletLpnSequence seq = sequenceRepository.findBySeqKeyForUpdate(seqKey)
                .orElseGet(() -> sequenceRepository.save(
                        PalletLpnSequence.builder().seqKey(seqKey).build()));
        long number = seq.getAndIncrement();
        sequenceRepository.save(seq);
        return number;
    }
}
