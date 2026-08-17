package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteRelatedIdentifier(String relatedIdentifier, String relatedIdentifierType, String relationType) {
}
