package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteDoiData(
        String id,
        String type,
        DataCiteAttributes attributes,
        DataCiteRelationships relationships) {
}
