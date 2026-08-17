package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = DataCiteAffiliationDeserializer.class)
public record DataCiteAffiliation(String name, String affiliationIdentifier, String affiliationIdentifierScheme) {
}
