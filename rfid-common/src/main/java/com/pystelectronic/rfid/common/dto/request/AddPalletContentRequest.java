package com.pystelectronic.rfid.common.dto.request;

import com.pystelectronic.rfid.common.enums.ContentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Request body unificado para POST /api/v1/pallets/{palletId}/contents — Spec §6.3
 * contentType=LPN: lpnCode requerido, lpnCode debe ser único global
 * contentType=LOOSE_ITEM: lpnCode debe ser nulo
 */
@Data
@Builder
public class AddPalletContentRequest {

    @NotNull(message = "El tipo de contenido es requerido")
    private ContentType contentType;

    @Pattern(regexp = "^[0-9]{14}$", message = "VAL-002: 14 dígitos numéricos — ej: 99950000272607")
    private String lpnCode;

    @Pattern(regexp = "^[0-9A-F]{24}$", message = "VAL-002: 24 caracteres hexadecimales — ej: E2806894000040038660A111")
    private String epc;

    private Boolean isKit;

    @Min(0)
    private Integer piecesInside;

    @NotEmpty(message = "Se requiere al menos un SKU")
    @Valid
    private List<SkuEntry> skus;

    @Data
    @Builder
    public static class SkuEntry {

        @NotBlank
        @Pattern(regexp = "^[0-9]{4,10}$", message = "VAL-002: 4 a 10 dígitos numéricos — ej: 126551")
        private String skuCode;

        @NotBlank
        @Size(max = 100)
        private String skuDescription;

        @NotNull
        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        private Integer unitQuantity;
    }
}
