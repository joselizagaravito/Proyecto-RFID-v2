package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByTransferCode(String transferCode);
    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT t FROM Transfer t LEFT JOIN FETCH t.pallets WHERE t.id = :id")
    Optional<Transfer> findByIdWithPallets(@Param("id") UUID id);
}