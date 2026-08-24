package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One {@code descriptions[]} entry, typed by its {@code descriptionType}.
 *
 * @param description     the description text
 * @param descriptionType its type; {@code Abstract} is the one mapped to abstracts
 * @param lang            the description's language tag, if given
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteDescription(
        String description,
        String descriptionType,
        String lang) {
}
