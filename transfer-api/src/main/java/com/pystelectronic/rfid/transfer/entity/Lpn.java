package com.pystelectronic.rfid.transfer.entity;

import com.pystelectronic.rfid.common.enums.LpnStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "lpn")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lpn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "lpn_code", nullable = false, unique = true, length = 14)
    private String lpnCode;

    @Column(name = "epc", unique = true, length = 36)
    private String epc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pallet_id", nullable = false)
    private Pallet pallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LpnStatus status = LpnStatus.CREATED;

    @Column(name = "is_kit", nullable = false)
    @Builder.Default
    private Boolean isKit = false;

    @Column(name = "pieces_inside", nullable = false)
    @Builder.Default
    private Integer piecesInside = 0;

    @Column(name = "total_units", nullable = false)
    @Builder.Default
    private Integer totalUnits = 0;

    @OneToMany(mappedBy = "lpn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<LpnSku> skus = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public void recalculateTotalUnits() {
        totalUnits = skus.stream().mapToInt(s -> s.getUnitQuantity() != null ? s.getUnitQuantity() : 0).sum();
    }
}
