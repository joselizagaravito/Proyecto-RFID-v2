package com.pystelectronic.rfid.validation.repository;

import com.pystelectronic.rfid.validation.entity.LpnView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LpnQueryRepository extends JpaRepository<LpnView, UUID> {

    Optional<LpnView> findByEpc(String epc);

    Optional<LpnView> findByLpnCode(String lpnCode);

    /**
     * Busca todos los LPNs activos de un traslado específico.
     * Se usa para calcular expected_count en Redis al iniciar sesión de lectura.
     */
    @Query("""
            SELECT l FROM LpnView l
            JOIN l.pallet p
            JOIN p.transfer t
            WHERE t.id = :transferId
              AND t.status IN ('DISPATCHED', 'IN_TRANSIT')
            """)
    List<LpnView> findByTransferId(@Param("transferId") UUID transferId);
}
