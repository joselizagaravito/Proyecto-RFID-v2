package com.pystelectronic.rfid.validation.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Vista de solo lectura de la tabla transfer.
 * Permite al validation-service consultar traslados activos sin depender
 * del transfer-api por HTTP (evita acoplamiento sincrónico en el hot path).
 */
@Entity
@Table(name = "transfer")
@Getter
public class TransferView {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "transfer_code")
    private String transferCode;

    @Column(name = "origin_code")
    private String originCode;

    @Column(name = "destination_code")
    private String destinationCode;

    /**
     * Solo los traslados en estado DISPATCHED o IN_TRANSIT son elegibles
     * para validación de EPCs de salida/entrada.
     */
    @Column(name = "status")
    private String status;

    @OneToMany(mappedBy = "transfer", fetch = FetchType.LAZY)
    private List<PalletView> pallets;
}
