package com.pystelectronic.rfid.transfer.service;

import com.pystelectronic.rfid.transfer.controller.dto.PalletTagRequest;
import com.pystelectronic.rfid.transfer.controller.dto.PalletTagResponse;
import com.pystelectronic.rfid.transfer.entity.PalletTag;
import com.pystelectronic.rfid.transfer.repository.PalletTagRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Lógica de negocio para pallet tags.
 * Sprint 8 — Pystelectronic · Ing. José Hernán Liza Garavito
 */
@Service
@Transactional(readOnly = true)
public class PalletTagService {

    private final PalletTagRepository repo;

    public PalletTagService(PalletTagRepository repo) {
        this.repo = repo;
    }

    /**
     * Listar con paginación.
     * @param q           Texto libre para filtrar por EPC o descripción (opcional)
     * @param soloActivos true = solo registros activos (default del portal)
     * @param pageable    Paginación Spring
     */
    public Page<PalletTagResponse> listar(String q, boolean soloActivos, Pageable pageable) {
        return repo.buscar(q, soloActivos, pageable).map(PalletTagResponse::from);
    }

    /** Obtener por ID. Lanza IllegalArgumentException si no existe. */
    public PalletTagResponse obtener(UUID id) {
        return repo.findById(id)
            .map(PalletTagResponse::from)
            .orElseThrow(() -> new IllegalArgumentException("PalletTag no encontrado: " + id));
    }

    /**
     * Crear nuevo pallet tag.
     * Normaliza el EPC (sin guiones, mayúsculas) y valida unicidad.
     */
    @Transactional
    public PalletTagResponse crear(PalletTagRequest req, String usuarioActual) {
        String epcNorm = req.epc().replace("-", "").toUpperCase();

        if (repo.existsByEpc(epcNorm)) {
            throw new IllegalStateException(
                "Ya existe un pallet tag con EPC: " + epcNorm);
        }

        PalletTag pt = new PalletTag();
        pt.setEpc(epcNorm);
        pt.setTid(req.tid());
        pt.setDescripcion(req.descripcion());
        pt.setActivo(true);
        pt.setCreatedBy(usuarioActual != null && !usuarioActual.isBlank()
            ? usuarioActual : "system");

        return PalletTagResponse.from(repo.save(pt));
    }

    /**
     * Soft-delete: marca activo=false.
     * No elimina el registro para mantener trazabilidad de auditoría.
     */
    @Transactional
    public void desactivar(UUID id) {
        PalletTag pt = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("PalletTag no encontrado: " + id));
        pt.setActivo(false);
        repo.save(pt);
    }

    /**
     * Eliminación física (solo ADMIN).
     * Perder la trazabilidad — usar con precaución.
     */
    @Transactional
    public void eliminar(UUID id) {
        if (!repo.existsById(id)) {
            throw new IllegalArgumentException("PalletTag no encontrado: " + id);
        }
        repo.deleteById(id);
    }
}
