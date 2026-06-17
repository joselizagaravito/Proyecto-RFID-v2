package com.pystelectronic.rfid.transfer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sesión de pallet por portal RFID.
 * Mantiene qué pallet está "activo" en cada portal para asociarle
 * los LPN leídos a continuación. Expira por inactividad (last_read_at).
 *
 * Sprint 9 · Pystelectronic
 */
@Entity
@Table(name = "portal_session")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PortalSession {

    @Id
    @Column(name = "portal_id", length = 64)
    private String portalId;

    @Column(name = "active_pallet_id")
    private UUID activePalletId;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "lpn_count", nullable = false)
    @Builder.Default
    private Integer lpnCount = 0;

    @Column(name = "opened_at", nullable = false)
    @Builder.Default
    private OffsetDateTime openedAt = OffsetDateTime.now();

    @Column(name = "last_read_at", nullable = false)
    @Builder.Default
    private OffsetDateTime lastReadAt = OffsetDateTime.now();

    public void touch() {
        this.lastReadAt = OffsetDateTime.now();
    }

    public void incrementLpn() {
        this.lpnCount = (this.lpnCount == null ? 0 : this.lpnCount) + 1;
        touch();
    }
}
