package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envelope of a Crossref single-work response.
 *
 * @param status  Crossref's own {@code ok}/error status string
 * @param message the single work this response carries
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWorkResponse(
        String status,
        CrossrefWork message) {
}
