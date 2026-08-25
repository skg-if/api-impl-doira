package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

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
        @Nullable String funderName,
        @Nullable String funderIdentifier,
        @Nullable String funderIdentifierType,
        @Nullable String awardNumber,
        @Nullable String awardTitle,
        @Nullable String awardUri) {
}
