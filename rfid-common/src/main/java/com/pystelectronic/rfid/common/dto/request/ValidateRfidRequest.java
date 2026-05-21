package com.pystelectronic.rfid.common.dto.request;

import com.pystelectronic.rfid.common.enums.DeviceType;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;

/** Request body para POST /api/v1/transfers/{transferId}/rfid-validations — Spec §6.7 */
@Data
@Builder
public class ValidateRfidRequest {

    @Pattern(regexp = "^[0-9]{14}$", message = "VAL-002: 14 dígitos numéricos")
    private String lpnCode;

    @Pattern(regexp = "^[0-9A-F]{24}$", message = "VAL-002: 24 caracteres hexadecimales")
    private String epc;

    @NotBlank
    @Size(max = 40)
    private String deviceId;

    @NotNull
    private DeviceType deviceType;

    @NotBlank
    @Size(max = 40)
    private String userId;

    @NotNull
    private OffsetDateTime readDateTime;
}
