package com.pystelectronic.rfid.validation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import java.util.UUID;

/**
 * Vista de solo lectura de la tabla lpn.
 * El validation-service NO escribe en esta tabla.
 * Solo consulta EPC → LPN → Transfer para validar.
 */
@Entity
@Table(name = "lpn")
@Getter
public class LpnView {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "lpn_code")
    private String lpnCode;

    @Column(name = "epc")
    private String epc;

    @Column(name = "status")
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pallet_id")
    private PalletView pallet;
}