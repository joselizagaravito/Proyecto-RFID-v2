package com.pystelectronic.rfid.common.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/** Request body para POST /api/v1/transfers/{transferId}/pallets — Spec §6.2 */
@Data
@Builder
public class CreatePalletRequest {

    @NotBlank(message = "El código de pallet es requerido")
    @Pattern(regexp = "^PL[0-9]{12}$", message = "VAL-002: Formato PL + 12 dígitos — ej: PL954001302868")
    private String palletCode;

    @DecimalMin(value = "0.0", inclusive = false, message = "El peso bruto debe ser positivo")
    @Digits(integer = 7, fraction = 2)
    private BigDecimal grossWeight;

    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 7, fraction = 2)
    private BigDecimal heightCm;

    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 7, fraction = 2)
    private BigDecimal widthCm;

    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 7, fraction = 2)
    private BigDecimal lengthCm;

    @Size(max = 250, message = "Las observaciones del pallet no pueden superar 250 caracteres")
    private String remarks;
}
