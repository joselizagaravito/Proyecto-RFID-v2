package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.PalletLpnSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PalletLpnSequenceRepository extends JpaRepository<PalletLpnSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PalletLpnSequence s WHERE s.seqKey = :seqKey")
    Optional<PalletLpnSequence> findBySeqKeyForUpdate(@Param("seqKey") String seqKey);
}
