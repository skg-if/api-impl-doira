package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A Crossref work's {@code resource} block, holding its publisher landing-page URL.
 *
 * @param primary the primary resource entry
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefResource(
        @Nullable Primary primary) {

    /**
     * The {@code resource.primary} entry, whose URL is the work's canonical landing page.
     *
     * @param url the work's canonical publisher landing-page URL
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Primary(
            @JsonProperty("URL") @Nullable String url) {
    }
}
