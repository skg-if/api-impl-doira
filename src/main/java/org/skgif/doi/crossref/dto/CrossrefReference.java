package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A {@code work.reference[]} entry - Crossref's structured citation list, and (verified live:
 * plain works commonly have an empty {@code relation} map even when {@code reference[]} is
 * populated) the actual source of "this work cites DOI X", unlike DataCite where that comes
 * from {@code relatedIdentifiers[relationType=Cites]}. Only entries the depositing publisher
 * asserted a DOI for ({@code doi-asserted-by}) carry one; {@code key}/{@code unstructured} are
 * kept as a fallback label so a DOI-less reference can still get an otf id instead of being
 * dropped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefReference {

    @JsonProperty("DOI")
    public String doi;

    @JsonProperty("key")
    public String key;

    @JsonProperty("unstructured")
    public String unstructured;
}
