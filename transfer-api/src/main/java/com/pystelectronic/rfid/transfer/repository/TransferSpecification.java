package com.pystelectronic.rfid.transfer.repository;

import com.pystelectronic.rfid.transfer.entity.Transfer;
import com.pystelectronic.rfid.transfer.controller.dto.TransferFilterRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification para consultas dinámicas de Transfer.
 *
 * Permite combinar cualquier subconjunto de filtros sin construir queries
 * JPQL con concatenación de strings. Cada filtro es un Predicate independiente.
 *
 * Uso:
 *   Specification<Transfer> spec = TransferSpecification.from(filter);
 *   Page<Transfer> page = transferRepository.findAll(spec, pageable);
 */
public final class TransferSpecification {

    private TransferSpecification() {}

    /**
     * Construye la Specification completa a partir del DTO de filtros.
     * Los filtros nulos/vacíos se ignoran automáticamente.
     */
    public static Specification<Transfer> from(TransferFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── Filtro por status (exact match) ─────────────────────────
            if (hasText(filter.status())) {
                predicates.add(cb.equal(root.get("status"), filter.status().trim()));
            }

            // ── Filtro por originCode (case-insensitive) ─────────────────
            if (hasText(filter.originCode())) {
                predicates.add(cb.equal(
                        cb.lower(root.get("originCode")),
                        filter.originCode().trim().toLowerCase()
                ));
            }

            // ── Filtro por destinationCode (case-insensitive) ────────────
            if (hasText(filter.destinationCode())) {
                predicates.add(cb.equal(
                        cb.lower(root.get("destinationCode")),
                        filter.destinationCode().trim().toLowerCase()
                ));
            }

            // ── Filtro por priority ──────────────────────────────────────
            if (hasText(filter.priority())) {
                predicates.add(cb.equal(root.get("priority"), filter.priority().trim()));
            }

            // ── Filtro por rango de scheduledDate ────────────────────────
            if (filter.scheduledDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("scheduledDate"), filter.scheduledDateFrom()
                ));
            }
            if (filter.scheduledDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("scheduledDate"), filter.scheduledDateTo()
                ));
            }

            // ── Búsqueda de texto libre (transferCode o carrierId) ───────
            if (hasText(filter.search())) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("transferCode")), pattern),
                        cb.like(cb.lower(root.get("carrierId")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
