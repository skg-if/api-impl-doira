package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefResource(Primary primary) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Primary(@JsonProperty("URL") String url) {
    }
}
