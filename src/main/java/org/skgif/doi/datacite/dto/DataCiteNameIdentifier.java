package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteNameIdentifier(String nameIdentifier, String nameIdentifierScheme, String schemeUri) {
}
