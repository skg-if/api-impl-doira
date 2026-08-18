package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefInvestigator(
                                   String given,
                                   String family,
                                   @JsonProperty("ORCID") String orcid,
                                   List<CrossrefAffiliation> affiliation) {
}
