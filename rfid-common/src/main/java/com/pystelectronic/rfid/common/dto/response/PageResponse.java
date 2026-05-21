package com.pystelectronic.rfid.common.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PageResponse<T> {
    private List<T> content;
    private long totalElements;
    private int page;
    private int size;
    private int totalPages;
    private boolean last;
}
