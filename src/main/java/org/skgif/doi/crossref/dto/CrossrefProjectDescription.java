package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One {@code project[].project-description[]} entry of a Crossref grant record.
 *
 * @param description the project description text
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefProjectDescription(
        String description) {
}
