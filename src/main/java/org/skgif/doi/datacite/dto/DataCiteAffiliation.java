package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * One {@code affiliation[]} entry of a DataCite creator or contributor.
 *
 * @param name                        the organisation name
 * @param affiliationIdentifier       the organisation identifier (typically a ROR URL)
 * @param affiliationIdentifierScheme the scheme that identifier belongs to (e.g. {@code ROR})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = DataCiteAffiliationDeserializer.class)
public record DataCiteAffiliation(
        String name,
        String affiliationIdentifier,
        String affiliationIdentifierScheme) {
}
