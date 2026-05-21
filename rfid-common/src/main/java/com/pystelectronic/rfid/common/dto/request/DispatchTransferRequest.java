package com.pystelectronic.rfid.common.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

/** Request body para POST /api/v1/transfers/{transferId}/dispatch — Spec §6.8 */
@Data
@Builder
public class DispatchTransferRequest {

    @NotNull(message = "La fecha de despacho es requerida")
    private OffsetDateTime dispatchDateTime;

    @NotBlank(message = "El usuario que confirma el despacho es requerido")
    @Size(max = 40, message = "El userId no puede superar 40 caracteres")
    private String userId;

    @Size(max = 20, message = "La placa no puede superar 20 caracteres")
    private String vehiclePlate;

    @Size(max = 30, message = "El número de guía no puede superar 30 caracteres")
    private String shippingNote;

    @NotEmpty(message = "Se requiere al menos un LPN para despachar")
    private List<String> lpnList;

    @Valid
    private List<LooseItemEntry> looseItemsList;

    @NotNull(message = "El total de unidades declaradas es requerido")
    @Min(value = 1, message = "El total de unidades debe ser mayor a 0")
    private Integer declaredTotalUnits;

    @Data
    @Builder
    public static class LooseItemEntry {
        @NotBlank
        @Pattern(regexp = "^[0-9]{4,10}$")
        private String skuCode;

        @NotNull
        @Min(1)
        private Integer unitQuantity;
    }
}
