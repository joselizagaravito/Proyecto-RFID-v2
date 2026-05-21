package com.pystelectronic.rfid.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pystelectronic.rfid.common.enums.IncidentType;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceiptResponse {
    private String receiptId;
    private String receiptStatus;
    private Integer expectedLpns;
    private Integer receivedLpns;
    private Integer expectedLooseItems;
    private Integer receivedLooseItems;
    private Integer expectedTotalUnits;
    private Integer receivedTotalUnits;
    private List<IncidentEntry> incidents;

    @Data
    @Builder
    public static class IncidentEntry {
        private IncidentType type;
        private String lpnCode;
        private String skuCode;
        private String skuDescription;
        private Integer expectedQty;
        private Integer receivedQty;
        private Integer unitQuantity;
        private String details;
    }
}
