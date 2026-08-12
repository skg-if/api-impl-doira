package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code {id, id-type, asserted-by}} shape Crossref uses for structured external
 * identifiers - shared by affiliation ids (usually a ROR) and funder ids (usually a Funder
 * Registry DOI), e.g. {@code affiliation[].id[]} and grant {@code project[].funding[].funder.id[]}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefIdEntry {

    public String id;
    @JsonProperty("id-type")
    public String idType;
    @JsonProperty("asserted-by")
    public String assertedBy;
}
