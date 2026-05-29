package com.pystelectronic.rfid.transfer.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad JPA — tabla pallet_tag.
 *
 * Registra los tags RFID físicos que identifican pallets.
 * La app C# (R2000Demo) sincroniza esta tabla a su SQL Server local
 * para clasificar lecturas como PALLET (azul) en lugar de LPN (verde).
 *
 * Sprint 8 — Pystelectronic · Ing. José Hernán Liza Garavito
 */
@Entity
@Table(
    name = "pallet_tag",
    uniqueConstraints = @UniqueConstraint(name = "uq_pallet_tag_epc", columnNames = "epc")
)
public class PalletTag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** EPC del chip RFID, sin guiones, mayúsculas. Ej: E200001D880C018010209CBA */
    @NotBlank(message = "El EPC es obligatorio")
    @Size(max = 50, message = "El EPC no puede superar 50 caracteres")
    @Pattern(
        regexp = "^[0-9A-Fa-f]{10,50}$",
        message = "El EPC debe ser hexadecimal sin guiones (10-50 caracteres)"
    )
    @Column(name = "epc", nullable = false, length = 50)
    private String epc;

    /** Tag Identifier del chip. Opcional. */
    @Size(max = 50)
    @Column(name = "tid", length = 50)
    private String tid;

    /** Descripción libre del pallet o tag físico. */
    @Size(max = 100)
    @Column(name = "descripcion", length = 100)
    private String descripcion;

    /** FALSE = desactivado (soft-delete). */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    /** Usuario del portal web que registró el tag. */
    @Size(max = 40)
    @Column(name = "created_by", nullable = false, length = 40)
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
        normalizarCampos();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    private void normalizarCampos() {
        if (epc != null) epc = epc.replace("-", "").toUpperCase();
        if (tid != null) tid = tid.replace("-", "").toUpperCase();
    }

    // ── Getters / Setters ────────────────────────────────────

    public UUID getId() { return id; }

    public String getEpc() { return epc; }
    public void setEpc(String epc) {
        this.epc = epc != null ? epc.replace("-", "").toUpperCase() : null;
    }

    public String getTid() { return tid; }
    public void setTid(String tid) {
        this.tid = tid != null ? tid.replace("-", "").toUpperCase() : null;
    }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
