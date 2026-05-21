package com.pystelectronic.rfid.transfer.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "lpn_sku")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LpnSku {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lpn_id", nullable = false)
    private Lpn lpn;

    @Column(name = "sku_code", nullable = false, length = 10)
    private String skuCode;

    @Column(name = "sku_description", nullable = false, length = 100)
    private String skuDescription;

    @Column(name = "unit_quantity", nullable = false)
    private Integer unitQuantity;
}
