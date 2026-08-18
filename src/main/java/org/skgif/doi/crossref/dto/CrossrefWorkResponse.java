package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWorkResponse(
        String status,
        CrossrefWork message) {
}
