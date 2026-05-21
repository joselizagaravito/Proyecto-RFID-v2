package com.pystelectronic.rfid.common.dto.response;

import com.pystelectronic.rfid.common.enums.LpnStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RfidValidationResponse {
    private String result;
    private String reason;
    private String lpnId;
    private LpnStatus lpnStatus;
}
