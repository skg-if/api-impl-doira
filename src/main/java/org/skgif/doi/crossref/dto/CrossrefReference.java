package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@code work.reference[]} entry - Crossref's structured citation list, and (verified live:
 * plain works commonly have an empty {@code relation} map even when {@code reference[]} is
 * populated) the actual source of "this work cites DOI X", unlike DataCite where that comes
 * from {@code relatedIdentifiers[relationType=Cites]}. Only entries the depositing publisher
 * asserted a DOI for ({@code doi-asserted-by}) carry one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefReference {

    @JsonProperty("DOI")
    public String doi;
}
