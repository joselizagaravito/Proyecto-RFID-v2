package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.Lpn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LpnRepository extends JpaRepository<Lpn, UUID> {
    boolean existsByLpnCode(String lpnCode);
    Optional<Lpn> findByLpnCode(String lpnCode);
    Optional<Lpn> findByEpc(String epc);
    List<Lpn> findByTransferId(UUID transferId);

    @Query("SELECT l FROM Lpn l LEFT JOIN FETCH l.skus WHERE l.lpnCode = :lpnCode AND l.transfer.id = :transferId")
    Optional<Lpn> findByLpnCodeAndTransferId(@Param("lpnCode") String lpnCode, @Param("transferId") UUID transferId);

    @Query("SELECT l FROM Lpn l LEFT JOIN FETCH l.skus WHERE l.epc = :epc AND l.transfer.id = :transferId")
    Optional<Lpn> findByEpcAndTransferId(@Param("epc") String epc, @Param("transferId") UUID transferId);
}
