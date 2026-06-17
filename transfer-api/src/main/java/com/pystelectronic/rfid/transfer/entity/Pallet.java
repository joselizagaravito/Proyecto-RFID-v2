package com.pystelectronic.rfid.transfer.entity;

import com.pystelectronic.rfid.common.enums.PalletStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pallet")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "pallet_code", nullable = false, unique = true, length = 14)
    private String palletCode;

    @Column(name = "epc", unique = true, length = 36)
    private String epc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PalletStatus status = PalletStatus.CREATED;

    @Column(name = "gross_weight", precision = 10, scale = 2)
    private BigDecimal grossWeight;

    @Column(name = "height_cm", precision = 10, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "width_cm", precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "length_cm", precision = 10, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "total_lpns", nullable = false)
    @Builder.Default
    private Integer totalLpns = 0;

    @Column(name = "total_loose_items", nullable = false)
    @Builder.Default
    private Integer totalLooseItems = 0;

    @Column(name = "total_units", nullable = false)
    @Builder.Default
    private Integer totalUnits = 0;

    @Column(name = "remarks", length = 250)
    private String remarks;

    @OneToMany(mappedBy = "pallet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Lpn> lpns = new ArrayList<>();

    @OneToMany(mappedBy = "pallet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LooseItem> looseItems = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public void recalculateTotals() {
        totalLpns = lpns.size();
        totalLooseItems = looseItems.size();
        totalUnits = lpns.stream().mapToInt(l -> l.getTotalUnits() != null ? l.getTotalUnits() : 0).sum()
                   + looseItems.stream().mapToInt(li -> li.getUnitQuantity() != null ? li.getUnitQuantity() : 0).sum();
    }
}
