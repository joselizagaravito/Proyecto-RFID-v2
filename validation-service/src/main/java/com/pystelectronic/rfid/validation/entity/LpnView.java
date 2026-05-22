package com.pystelectronic.rfid.validation.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.UUID;

/**
 * Vista de solo lectura de la tabla lpn.
 * El validation-service NO escribe en esta tabla (responsabilidad del transfer-api).
 * Solo necesita consultar el EPC → LPN → Transfer para validar.
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

    @Column(name = "sku_code")
    private String skuCode;

    @Column(name = "unit_quantity")
    private Integer unitQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pallet_id")
    private PalletView pallet;
}
