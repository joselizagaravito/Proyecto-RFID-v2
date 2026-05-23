package com.pystelectronic.rfid.readtag.repository;

import com.pystelectronic.rfid.readtag.entity.ReadTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReadTagRepository extends JpaRepository<ReadTag, UUID> {

    Optional<ReadTag> findByEpc(String epc);

    @Query("""
            SELECT r FROM ReadTag r
            WHERE (:epc      IS NULL OR r.epc      = :epc)
              AND (:moduloId IS NULL OR r.moduloId = :moduloId)
              AND (CAST(:startDate AS java.time.LocalDateTime) IS NULL OR r.lastTime >= :startDate)
              AND (CAST(:endDate   AS java.time.LocalDateTime) IS NULL OR r.lastTime <= :endDate)
            ORDER BY r.lastTime DESC NULLS LAST
            """)
    Page<ReadTag> findWithFilters(
            @Param("epc")       String epc,
            @Param("moduloId")  String moduloId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate,
            Pageable pageable);
}