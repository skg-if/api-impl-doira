package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteFundingReference(
                                       String funderName,
                                       String funderIdentifier,
                                       String funderIdentifierType,
                                       String awardNumber,
                                       String awardTitle,
                                       String awardUri) {
}
