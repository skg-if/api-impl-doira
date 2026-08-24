package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One {@code fundingReferences[]} entry of a DataCite record.
 *
 * @param funderName           the funding organisation's name
 * @param funderIdentifier     the funder's identifier, typically a Funder Registry or ROR URL
 * @param funderIdentifierType the scheme that identifier belongs to
 * @param awardNumber          the grant number
 * @param awardTitle           the grant title
 * @param awardUri             a URL for the grant
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteFundingReference(
        String funderName,
        String funderIdentifier,
        String funderIdentifierType,
        String awardNumber,
        String awardTitle,
        String awardUri) {
}
