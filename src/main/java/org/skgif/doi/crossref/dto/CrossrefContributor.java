package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One {@code author}/{@code editor} entry of a Crossref work.
 *
 * @param given       the contributor's given name(s)
 * @param family      the contributor's family name
 * @param sequence    Crossref's ordering marker ({@code first} for the lead author)
 * @param orcid       the contributor's ORCID URL, if Crossref recorded one
 * @param affiliation the contributor's affiliations
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefContributor(
        String given,
        String family,
        String sequence,
        @JsonProperty("ORCID") String orcid,
        List<CrossrefAffiliation> affiliation) {
}
