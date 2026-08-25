package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A top-level {@code work.funder[]} entry carries the Funder Registry DOI directly as {@code
 * DOI}; a grant record's {@code project[].funding[].funder} carries the same DOI only inside
 * {@code id[]} (verified live against a real Wellcome Trust grant record) - both shapes are
 * modeled here, see {@code CrossrefFundingMapper#funderDoi}.
 *
 * @param name  the funder name
 * @param doi   the Funder Registry DOI, when present directly on this entry
 * @param award award numbers granted by this funder
 * @param id    structured external identifiers for the funder, sometimes including the Funder
 *              Registry DOI instead of (or as well as) {@code doi}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefFunder(
        @Nullable String name,
        @JsonProperty("DOI") @Nullable String doi,
        @Nullable List<String> award,
        @Nullable List<CrossrefIdEntry> id) {
}
