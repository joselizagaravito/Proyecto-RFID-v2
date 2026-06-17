package com.pystelectronic.rfid.transfer.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Secuencia diaria para generar códigos de pallet y LPN.
 * Patrón idéntico a TransferSequence pero con clave por tipo+fecha.
 *
 * seq_key = "{tipo}-{YYMMDD}"  →  ej: "PL-260617", "LPN-260617"
 *
 * Sprint 9 · Pystelectronic
 */
@Entity
@Table(name = "pallet_lpn_sequence")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PalletLpnSequence {

    @Id
    @Column(name = "seq_key", length = 16)
    private String seqKey;

    @Column(name = "next_val", nullable = false)
    @Builder.Default
    private Long nextVal = 1L;

    public long getAndIncrement() {
        long current = nextVal;
        nextVal++;
        return current;
    }
}
