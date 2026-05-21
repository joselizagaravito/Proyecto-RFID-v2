package com.pystelectronic.rfid.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pystelectronic.rfid.common.enums.ContentType;
import com.pystelectronic.rfid.common.enums.LpnStatus;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentItemResponse {
    private ContentType contentType;
    private String lpnId;
    private String lpnCode;
    private String epc;
    private Boolean isKit;
    private Integer piecesInside;
    private LpnStatus status;
    private List<SkuResponse> skus;
    private Integer totalUnits;
    private String looseItemId;
    private String skuCode;
    private String skuDescription;
    private Integer unitQuantity;

    @Data
    @Builder
    public static class SkuResponse {
        private String skuCode;
        private String skuDescription;
        private Integer unitQuantity;
    }
}
