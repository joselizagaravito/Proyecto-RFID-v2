package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.RfidEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RfidEventRepository extends JpaRepository<RfidEvent, UUID> {

    Page<RfidEvent> findByTransferId(UUID transferId, Pageable pageable);

    @Query(value = """
        SELECT * FROM rfid_event
        WHERE (:eventType IS NULL OR event_type = CAST(:eventType AS varchar))
          AND (:result    IS NULL OR result     = CAST(:result    AS varchar))
          AND (:lpnCode   IS NULL OR lpn_code   LIKE CONCAT('%', CAST(:lpnCode AS varchar), '%'))
        ORDER BY timestamp DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM rfid_event
        WHERE (:eventType IS NULL OR event_type = CAST(:eventType AS varchar))
          AND (:result    IS NULL OR result     = CAST(:result    AS varchar))
          AND (:lpnCode   IS NULL OR lpn_code   LIKE CONCAT('%', CAST(:lpnCode AS varchar), '%'))
        """,
        nativeQuery = true)
    Page<RfidEvent> findWithFilters(
        @Param("eventType") String eventType,
        @Param("result")    String result,
        @Param("lpnCode")   String lpnCode,
        Pageable pageable
    );
}