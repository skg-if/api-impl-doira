package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * One {@code license[]} entry of a Crossref work, carrying the licence URL.
 *
 * @param url the licence URL, matched against known Creative Commons URLs
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefLicense(
        @JsonProperty("URL") @Nullable String url) {
}
