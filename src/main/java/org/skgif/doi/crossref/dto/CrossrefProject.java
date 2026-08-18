package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A grant-type Crossref record's {@code project[]} entry - the source of most Grant fields
 * (grant_number comes from the parent {@link CrossrefWork#award()} instead, since it's shared
 * across every project on the DOI). A single grant DOI can carry multiple projects; see {@code
 * CrossrefToSkgIfMapper#toGrant} for how their titles/abstracts are folded together.
 *
 * @param projectTitle       the project's title(s)
 * @param projectDescription the project's abstract/description(s)
 * @param investigator       the project's investigators
 * @param leadInvestigator   the project's lead investigator(s)
 * @param awardAmount        the total award amount
 * @param awardStart         the award start date
 * @param awardEnd           the award end date
 * @param funding            the project's per-funder funding breakdown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefProject(
        @JsonProperty("project-title") List<CrossrefProjectTitle> projectTitle,
        @JsonProperty("project-description") List<CrossrefProjectDescription> projectDescription,
        List<CrossrefInvestigator> investigator,
        @JsonProperty("lead-investigator") List<CrossrefInvestigator> leadInvestigator,
        @JsonProperty("award-amount") CrossrefAmount awardAmount,
        @JsonProperty("award-start") CrossrefDate awardStart,
        @JsonProperty("award-end") CrossrefDate awardEnd,
        List<CrossrefFunding> funding) {
}
