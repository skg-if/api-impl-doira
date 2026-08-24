package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One {@code rightsList[]} entry, carrying a licence name, URL and identifier.
 *
 * @param rights           the licence's human-readable name
 * @param rightsUri        the licence URL, matched against known Creative Commons URLs
 * @param rightsIdentifier the licence's short identifier (e.g. {@code cc-by-4.0})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteRights(
        String rights,
        String rightsUri,
        String rightsIdentifier) {
}
