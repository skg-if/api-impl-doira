package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Envelope of a Crossref single-work response.
 *
 * @param status  Crossref's own {@code ok}/error status string
 * @param message the single work this response carries
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWorkResponse(
        @Nullable String status,
        @Nullable CrossrefWork message) {
}
