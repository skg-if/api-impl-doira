package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single {@code work.update-to[]} entry - Crossref's record of a correction/retraction/etc.
 * applied to this work. {@code type} is documented with only two example values ({@code
 * "correction"}, {@code "retraction"}) rather than an exhaustive enum, so {@code
 * CrossrefManifestationMapper#dates} only recognizes those two.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefUpdateTo {

    public CrossrefDate updated;
    @JsonProperty("DOI")
    public String doi;
    public String type;
    public String label;
}
