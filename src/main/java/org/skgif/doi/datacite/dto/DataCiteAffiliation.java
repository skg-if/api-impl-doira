package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.jspecify.annotations.Nullable;

/**
 * One {@code affiliation[]} entry of a DataCite creator or contributor.
 *
 * @param name                        the organisation name
 * @param affiliationIdentifier       the organisation identifier (typically a ROR URL)
 * @param affiliationIdentifierScheme the scheme that identifier belongs to (e.g. {@code ROR})
 */
@JsonDeserialize(using = DataCiteAffiliationDeserializer.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteAffiliation(
        @Nullable String name,
        @Nullable String affiliationIdentifier,
        @Nullable String affiliationIdentifierScheme) {
}
