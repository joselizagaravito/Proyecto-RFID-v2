package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.ReadTagView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReadTagQueryRepository extends JpaRepository<ReadTagView, UUID> {

    Page<ReadTagView> findByTransferId(UUID transferId, Pageable pageable);

    // Nota: ORDER BY fijo en la query para evitar que Spring Data use nombres de campo Java
    @Query(value = """
        SELECT * FROM read_tag
        WHERE (CAST(:epc AS varchar)       IS NULL OR epc        LIKE CONCAT('%', CAST(:epc AS varchar), '%'))
          AND (CAST(:deviceId AS varchar)  IS NULL OR device_id   = CAST(:deviceId  AS varchar))
          AND (CAST(:eventType AS varchar) IS NULL OR event_type  = CAST(:eventType AS varchar))
          AND (CAST(:result AS varchar)    IS NULL OR result      = CAST(:result    AS varchar))
        ORDER BY read_datetime DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM read_tag
        WHERE (CAST(:epc AS varchar)       IS NULL OR epc        LIKE CONCAT('%', CAST(:epc AS varchar), '%'))
          AND (CAST(:deviceId AS varchar)  IS NULL OR device_id   = CAST(:deviceId  AS varchar))
          AND (CAST(:eventType AS varchar) IS NULL OR event_type  = CAST(:eventType AS varchar))
          AND (CAST(:result AS varchar)    IS NULL OR result      = CAST(:result    AS varchar))
        """,
        nativeQuery = true)
    Page<ReadTagView> findWithFilters(
        @Param("epc")       String epc,
        @Param("deviceId")  String deviceId,
        @Param("eventType") String eventType,
        @Param("result")    String result,
        Pageable pageable
    );
}