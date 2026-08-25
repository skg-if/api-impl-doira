package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One {@code relatedIdentifiers[]} entry, typed by its relation to this DOI.
 *
 * @param relatedIdentifier     the related resource's identifier
 * @param relatedIdentifierType the scheme that identifier belongs to (e.g. {@code DOI})
 * @param relationType          how the related resource relates to this one (e.g. {@code IsPartOf})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteRelatedIdentifier(
        @Nullable String relatedIdentifier,
        @Nullable String relatedIdentifierType,
        @Nullable String relationType) {
}
