package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One {@code project[].project-title[]} entry of a Crossref grant record.
 *
 * @param title the project title text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefProjectTitle(
        @Nullable String title) {
}
