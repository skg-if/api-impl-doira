package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefWorkListResponse {

    public String status;
    public Message message;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("total-results")
        public long totalResults;
        public List<CrossrefWork> items;
    }
}
