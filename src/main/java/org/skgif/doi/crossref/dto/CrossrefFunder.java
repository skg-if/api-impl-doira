package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A top-level {@code work.funder[]} entry carries the Funder Registry DOI directly as {@code
 * DOI}; a grant record's {@code project[].funding[].funder} carries the same DOI only inside
 * {@code id[]} (verified live against a real Wellcome Trust grant record) - both shapes are
 * modeled here, see {@code CrossrefToSkgIfMapper#funderDoi}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefFunder {

    public String name;
    @JsonProperty("DOI")
    public String doi;
    public List<String> award;
    public List<CrossrefIdEntry> id;
}
