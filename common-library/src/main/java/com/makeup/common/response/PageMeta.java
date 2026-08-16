package com.makeup.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageMeta {
    private int page;
    private int limit;

    @JsonProperty("total_pages")
    private int totalPages;

    @JsonProperty("total_records")
    private long totalRecords;
}
