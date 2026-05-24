package com.pystelectronic.rfid.audit.repository;

import com.pystelectronic.rfid.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.createdAt BETWEEN :startDate AND :endDate
          AND (:userId       IS NULL OR a.userId       = :userId)
          AND (:clientIp     IS NULL OR a.clientIp     = :clientIp)
          AND (:httpMethod   IS NULL OR a.httpMethod   = :httpMethod)
          AND (:httpStatus   IS NULL OR a.httpStatus   = :httpStatus)
          AND (:auditLevel   IS NULL OR a.auditLevel   = :auditLevel)
          AND (:endpointPath IS NULL OR a.endpointPath LIKE :endpointPath)
        """)
    Page<AuditLog> buscarConFiltros(
        @Param("startDate")    OffsetDateTime startDate,
        @Param("endDate")      OffsetDateTime endDate,
        @Param("userId")       String userId,
        @Param("clientIp")     String clientIp,
        @Param("httpMethod")   String httpMethod,
        @Param("httpStatus")   Short httpStatus,
        @Param("auditLevel")   String auditLevel,
        @Param("endpointPath") String endpointPath,
        Pageable pageable
    );
}