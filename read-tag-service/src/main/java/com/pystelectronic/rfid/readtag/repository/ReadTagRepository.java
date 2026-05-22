package com.pystelectronic.rfid.readtag.repository;

import com.pystelectronic.rfid.readtag.entity.ReadTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReadTagRepository extends JpaRepository<ReadTag, Long> {

    Optional<ReadTag> findByEpc(String epc);

    boolean existsByEpc(String epc);

    List<ReadTag> findByModuloIdOrderByLastTimeDesc(String moduloId);

    @Query("""
            SELECT r FROM ReadTag r
            WHERE r.lastTime >= :desde
            ORDER BY r.lastTime DESC
            """)
    List<ReadTag> findRecentReads(@Param("desde") LocalDateTime desde);
}
