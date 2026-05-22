package com.pystelectronic.rfid.validation.repository;

import com.pystelectronic.rfid.validation.entity.TransferView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferQueryRepository extends JpaRepository<TransferView, UUID> {

    /**
     * Traslados elegibles para validación de EPCs.
     * Solo DISPATCHED e IN_TRANSIT reciben lecturas válidas.
     */
    @Query("SELECT t FROM TransferView t WHERE t.status IN ('DISPATCHED', 'IN_TRANSIT')")
    List<TransferView> findActiveTransfers();
}
