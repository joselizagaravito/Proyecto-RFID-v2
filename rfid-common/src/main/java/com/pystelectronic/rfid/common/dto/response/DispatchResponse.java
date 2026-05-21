package com.pystelectronic.rfid.common.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pystelectronic.rfid.common.enums.TransferStatus;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@Builder
public class DispatchResponse {
    private String transferId;
    private TransferStatus status;
    private Integer dispatchedLpnCount;
    private Integer dispatchedLooseItemCount;
    private Integer dispatchedTotalUnits;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime dispatchDateTime;
}
