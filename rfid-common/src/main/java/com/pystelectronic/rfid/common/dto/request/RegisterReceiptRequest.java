package com.pystelectronic.rfid.common.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

/** Request body para POST /api/v1/transfers/{transferId}/receipts — Spec §6.9 */
@Data
@Builder
public class RegisterReceiptRequest {

    @NotNull(message = "La fecha de recepción es requerida")
    private OffsetDateTime receiptDateTime;

    @NotBlank(message = "El usuario que registra la recepción es requerido")
    @Size(max = 40)
    private String userId;

    @Pattern(regexp = "^PL[0-9]{12}$")
    private String palletCode;

    @NotEmpty(message = "Se requiere al menos una lectura")
    @Valid
    private List<ReadingEntry> readings;

    @Valid
    private List<LooseItemEntry> receivedLooseItems;

    @Size(max = 500)
    private String remarks;

    @Data
    @Builder
    public static class ReadingEntry {
        private String lpnCode;
        private String epc;
        private String deviceId;
        private String deviceType;
    }

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
