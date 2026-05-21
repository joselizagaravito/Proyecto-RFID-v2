package com.pystelectronic.rfid.transfer.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "loose_item")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LooseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pallet_id", nullable = false)
    private Pallet pallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Column(name = "sku_code", nullable = false, length = 10)
    private String skuCode;

    @Column(name = "sku_description", nullable = false, length = 100)
    private String skuDescription;

    @Column(name = "unit_quantity", nullable = false)
    private Integer unitQuantity;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "REGISTERED";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
