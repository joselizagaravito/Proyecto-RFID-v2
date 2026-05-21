package com.pystelectronic.rfid.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReconciliationResponse {
    private String transferId;
    private Integer totalDispatched;
    private Integer totalReceived;
    private Integer lpnDifference;
    private Integer looseItemDifference;
    private Integer unitDifference;
    private List<ReceiptResponse.IncidentEntry> incidents;
    private String result;
}
