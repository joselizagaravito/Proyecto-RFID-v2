package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.PortalSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PortalSessionRepository extends JpaRepository<PortalSession, String> {

    /**
     * Obtiene la sesión del portal con bloqueo de escritura.
     * Garantiza que dos lecturas concurrentes del mismo portal no compitan.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PortalSession s WHERE s.portalId = :portalId")
    Optional<PortalSession> findByPortalIdForUpdate(@Param("portalId") String portalId);

    /**
     * Sesiones cuya última lectura es anterior al umbral → candidatas a expirar.
     */
    @Query("SELECT s FROM PortalSession s WHERE s.lastReadAt < :threshold AND s.activePalletId IS NOT NULL")
    List<PortalSession> findExpiredSessions(@Param("threshold") OffsetDateTime threshold);
}
