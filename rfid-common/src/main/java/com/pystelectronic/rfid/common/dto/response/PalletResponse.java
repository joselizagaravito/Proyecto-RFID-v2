package com.pystelectronic.rfid.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pystelectronic.rfid.common.enums.PalletStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PalletResponse {
    private String palletId;
    private String palletCode;
    private String transferId;
    private PalletStatus status;
    private BigDecimal grossWeight;
    private BigDecimal heightCm;
    private BigDecimal widthCm;
    private BigDecimal lengthCm;
    private Integer totalLpns;
    private Integer totalLooseItems;
    private Integer totalUnits;
    private List<ContentItemResponse> contents;
}
