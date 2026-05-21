package com.pystelectronic.rfid.transfer.entity;

import com.pystelectronic.rfid.common.enums.TransferPriority;
import com.pystelectronic.rfid.common.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transfer")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transfer_code", nullable = false, unique = true, length = 25)
    private String transferCode;

    @Column(name = "origin_code", nullable = false, length = 10)
    private String originCode;

    @Column(name = "destination_code", nullable = false, length = 10)
    private String destinationCode;

    @Column(name = "status", nullable = false, columnDefinition = "transfer_status")
    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    @Column(name = "priority", nullable = false, columnDefinition = "transfer_priority")
    @Enumerated(EnumType.STRING)
    private TransferPriority priority;

    @Column(name = "carrier_id", length = 36)
    private String carrierId;

    @Column(name = "scheduled_date", nullable = false)
    private OffsetDateTime scheduledDate;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "shipping_note", length = 30)
    private String shippingNote;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "total_pallets", nullable = false)
    @Builder.Default
    private Integer totalPallets = 0;

    @Column(name = "total_lpns", nullable = false)
    @Builder.Default
    private Integer totalLpns = 0;

    @Column(name = "total_loose_items", nullable = false)
    @Builder.Default
    private Integer totalLooseItems = 0;

    @Column(name = "total_units", nullable = false)
    @Builder.Default
    private Integer totalUnits = 0;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Pallet> pallets = new ArrayList<>();

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 40)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 40)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public void addPallet(Pallet pallet) {
        pallet.setTransfer(this);
        pallets.add(pallet);
        totalPallets = pallets.size();
    }

    public void recalculateTotals() {
        totalPallets = pallets.size();
        totalLpns = pallets.stream().mapToInt(p -> p.getTotalLpns() != null ? p.getTotalLpns() : 0).sum();
        totalLooseItems = pallets.stream().mapToInt(p -> p.getTotalLooseItems() != null ? p.getTotalLooseItems() : 0).sum();
        totalUnits = pallets.stream().mapToInt(p -> p.getTotalUnits() != null ? p.getTotalUnits() : 0).sum();
    }

    public boolean isEditable() {
        return status == TransferStatus.DRAFT || status == TransferStatus.PREPARED;
    }

    public boolean isDispatchable() {
        return status == TransferStatus.PREPARED && !pallets.isEmpty();
    }
}