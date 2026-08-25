package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Envelope of a DataCite {@code dois} list/search response.
 *
 * @param data  the DOI records on this page
 * @param meta  the pagination counters
 * @param links DataCite's own navigation links for this page
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteDoiListResponse(
        @Nullable List<DataCiteDoiData> data,
        @Nullable Meta meta,
        @Nullable Map<String, String> links) {

    /**
     * The {@code meta} block's pagination counters.
     *
     * @param total      the total number of matches
     * @param totalPages the number of pages available
     * @param page       the 1-based index of this page
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            long total,
            int totalPages,
            int page) {
    }
}
