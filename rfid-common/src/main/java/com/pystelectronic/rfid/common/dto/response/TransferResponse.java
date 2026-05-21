package com.pystelectronic.rfid.common.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.pystelectronic.rfid.common.enums.TransferPriority;
import com.pystelectronic.rfid.common.enums.TransferStatus;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferResponse {
    private String transferId;
    private String transferCode;
    private String originCode;
    private String destinationCode;
    private TransferStatus status;
    private TransferPriority priority;
    private String carrierId;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime scheduledDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime dispatchedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime receivedAt;
    private String vehiclePlate;
    private String shippingNote;
    private String remarks;
    private Integer totalPallets;
    private Integer totalLpns;
    private Integer totalLooseItems;
    private Integer totalUnits;
    private List<PalletResponse> pallets;
}
