package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One {@code nameIdentifiers[]} entry (e.g. an ORCID) of a creator or contributor.
 *
 * @param nameIdentifier       the identifier value (e.g. an ORCID URL)
 * @param nameIdentifierScheme the scheme it belongs to (e.g. {@code ORCID})
 * @param schemeUri            the scheme's own URI
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteNameIdentifier(
        String nameIdentifier,
        String nameIdentifierScheme,
        String schemeUri) {
}
