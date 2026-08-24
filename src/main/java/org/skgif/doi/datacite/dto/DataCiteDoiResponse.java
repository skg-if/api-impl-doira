package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envelope of a DataCite single-DOI response.
 *
 * @param data the single DOI record this response carries
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteDoiResponse(
        DataCiteDoiData data) {
}
