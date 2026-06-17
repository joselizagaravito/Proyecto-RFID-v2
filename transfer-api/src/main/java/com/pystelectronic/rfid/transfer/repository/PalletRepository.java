package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.Pallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PalletRepository extends JpaRepository<Pallet, UUID> {
    boolean existsByPalletCodeAndTransferId(String palletCode, UUID transferId);
    boolean existsByPalletCode(String palletCode);
    List<Pallet> findByTransferId(UUID transferId);

    @Query("""
        SELECT p FROM Pallet p
        LEFT JOIN FETCH p.lpns l
        LEFT JOIN FETCH l.skus
        LEFT JOIN FETCH p.looseItems
        WHERE p.id = :id
        """)
    Optional<Pallet> findByIdWithContents(@Param("id") UUID id);

    java.util.Optional<Pallet> findByEpc(String epc);

    @Query("SELECT p FROM Pallet p WHERE p.epc = :epc AND p.transfer.id = :transferId")
    java.util.Optional<Pallet> findByEpcAndTransferId(
            @Param("epc") String epc, @Param("transferId") UUID transferId);
}
