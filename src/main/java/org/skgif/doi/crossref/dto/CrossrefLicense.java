package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefLicense(
        @JsonProperty("URL") String url) {
}
