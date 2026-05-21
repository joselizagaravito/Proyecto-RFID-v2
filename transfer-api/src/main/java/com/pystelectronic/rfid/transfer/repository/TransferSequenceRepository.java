package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.TransferSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TransferSequenceRepository extends JpaRepository<TransferSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TransferSequence s WHERE s.dateKey = :dateKey")
    Optional<TransferSequence> findByDateKeyForUpdate(@Param("dateKey") String dateKey);
}
