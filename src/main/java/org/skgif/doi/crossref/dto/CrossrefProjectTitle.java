package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One {@code project[].project-title[]} entry of a Crossref grant record.
 *
 * @param title the project title text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefProjectTitle(
        String title) {
}
