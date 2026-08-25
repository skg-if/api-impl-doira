package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A single {@code work.update-to[]} entry - Crossref's record of a correction/retraction/etc.
 * applied to this work. {@code type} is documented with only two example values ({@code
 * "correction"}, {@code "retraction"}) rather than an exhaustive enum, so {@code
 * CrossrefManifestationMapper#dates} only recognizes those two.
 *
 * @param updated the date the update was applied
 * @param doi     the DOI of the update record (e.g. the correction/retraction notice)
 * @param type    the kind of update (e.g. {@code "correction"}, {@code "retraction"})
 * @param label   a human-readable label for the update
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefUpdateTo(
        @Nullable CrossrefDate updated,
        @JsonProperty("DOI") @Nullable String doi,
        @Nullable String type,
        @Nullable String label) {
}
