package com.pystelectronic.rfid.common.dto.request;

import com.pystelectronic.rfid.common.enums.TransferPriority;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Request body para POST /api/v1/transfers
 * Spec §6.1
 */
@Data
@Builder
public class CreateTransferRequest {

    @NotBlank(message = "El código de origen es requerido")
    @Size(min = 1, max = 10, message = "El código de origen debe tener entre 1 y 10 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "VAL-001: Máx. 10 caracteres alfanuméricos")
    private String originCode;

    @NotBlank(message = "El código de destino es requerido")
    @Size(min = 1, max = 10, message = "El código de destino debe tener entre 1 y 10 caracteres")
    @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "VAL-001: Máx. 10 caracteres alfanuméricos")
    private String destinationCode;

    @NotNull(message = "La fecha programada es requerida")
    @Future(message = "La fecha programada debe ser futura")
    private OffsetDateTime scheduledDate;

    @NotNull(message = "La prioridad es requerida")
    private TransferPriority priority;

    @Size(max = 36, message = "El ID del transportista no puede superar 36 caracteres")
    private String carrierId;

    @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres")
    private String remarks;
}
