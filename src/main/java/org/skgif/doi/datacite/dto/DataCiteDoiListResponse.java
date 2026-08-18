package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteDoiListResponse(
        List<DataCiteDoiData> data,
        Meta meta,
        Map<String, String> links) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            long total,
            int totalPages,
            int page) {
    }
}
