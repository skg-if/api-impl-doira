package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Envelope of a DataCite single-DOI response.
 *
 * @param data the single DOI record this response carries
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteDoiResponse(
        @Nullable DataCiteDoiData data) {
}
