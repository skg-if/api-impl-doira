package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Envelope of a Crossref {@code works} list/search response.
 *
 * @param status  Crossref's own {@code ok}/error status string
 * @param message the payload carrying this page of works
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefWorkListResponse(
        String status,
        Message message) {

    /**
     * The {@code message} payload of a list response: this page of works plus the total count.
     *
     * @param totalResults the total number of matches, used to build pagination metadata
     * @param items        the works on this page
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("total-results") long totalResults,
            List<CrossrefWork> items) {
    }
}
