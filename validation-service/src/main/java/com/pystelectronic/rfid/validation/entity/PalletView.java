package com.pystelectronic.rfid.validation.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

/**
 * Vista de solo lectura de la tabla pallet.
 */
@Entity
@Table(name = "pallet")
@Getter
public class PalletView {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "pallet_code")
    private String palletCode;

    @Column(name = "status")
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private TransferView transfer;
}
