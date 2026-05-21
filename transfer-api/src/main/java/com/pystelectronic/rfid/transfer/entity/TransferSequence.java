package com.pystelectronic.rfid.transfer.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transfer_sequence")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferSequence {
    @Id
    @Column(name = "date_key", length = 8)
    private String dateKey;

    @Column(name = "next_val", nullable = false)
    @Builder.Default
    private Long nextVal = 1L;

    public long getAndIncrement() {
        long current = nextVal;
        nextVal++;
        return current;
    }
}
