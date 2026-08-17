package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWorkListResponse(String status, Message message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("total-results") long totalResults, List<CrossrefWork> items) {
    }
}
