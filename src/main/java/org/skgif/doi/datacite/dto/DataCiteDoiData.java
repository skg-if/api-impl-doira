package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * The {@code data} member of a DataCite DOI response: one DOI's id, type and metadata.
 *
 * @param id            the DOI, as the JSON:API resource id
 * @param type          the JSON:API resource type, always {@code dois}
 * @param attributes    the DOI's metadata
 * @param relationships the DOI's relationships, used here for the registering client
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteDoiData(
        @Nullable String id,
        @Nullable String type,
        @Nullable DataCiteAttributes attributes,
        @Nullable DataCiteRelationships relationships) {
}
