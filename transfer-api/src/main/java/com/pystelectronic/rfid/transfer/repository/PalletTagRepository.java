package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.PalletTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para pallet_tag.
 * Sprint 8 — Pystelectronic
 */
public interface PalletTagRepository extends JpaRepository<PalletTag, UUID> {

    /** Buscar por EPC exacto (ya normalizado: sin guiones, mayúsculas). */
    Optional<PalletTag> findByEpc(String epc);

    /** ¿Ya existe un tag con ese EPC? Usado para validar unicidad. */
    boolean existsByEpc(String epc);

    /** Listar solo los activos con paginación. */
    Page<PalletTag> findByActivoTrue(Pageable pageable);

    /**
     * Búsqueda por texto libre en EPC o descripción.
     * Usado por GET /pallet-tags?q=...
     */
    @Query("""
        SELECT pt FROM PalletTag pt
        WHERE (:q IS NULL OR :q = ''
               OR UPPER(pt.epc)         LIKE UPPER(CONCAT('%', :q, '%'))
               OR UPPER(pt.descripcion) LIKE UPPER(CONCAT('%', :q, '%')))
        AND (:soloActivos = false OR pt.activo = true)
    """)
    Page<PalletTag> buscar(
        @Param("q") String q,
        @Param("soloActivos") boolean soloActivos,
        Pageable pageable
    );
}
