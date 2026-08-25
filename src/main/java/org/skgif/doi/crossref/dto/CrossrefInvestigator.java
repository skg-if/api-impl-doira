package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One {@code project[].investigator[]} entry of a Crossref grant record.
 *
 * @param given       the investigator's given name(s)
 * @param family      the investigator's family name
 * @param orcid       the investigator's ORCID URL, if Crossref recorded one
 * @param affiliation the investigator's affiliations
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefInvestigator(
        @Nullable String given,
        @Nullable String family,
        @JsonProperty("ORCID") @Nullable String orcid,
        @Nullable List<CrossrefAffiliation> affiliation) {
}
