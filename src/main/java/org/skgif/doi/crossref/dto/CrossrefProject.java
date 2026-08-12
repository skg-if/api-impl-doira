package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A grant-type Crossref record's {@code project[]} entry - the source of most Grant fields
 * (grant_number comes from the parent {@link CrossrefWork#award} instead, since it's shared
 * across every project on the DOI). A single grant DOI can carry multiple projects; see {@code
 * CrossrefToSkgIfMapper#toGrant} for how their titles/abstracts are folded together.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefProject {

    @JsonProperty("project-title")
    public List<CrossrefProjectTitle> projectTitle;
    @JsonProperty("project-description")
    public List<CrossrefProjectDescription> projectDescription;
    public List<CrossrefInvestigator> investigator;
    @JsonProperty("lead-investigator")
    public List<CrossrefInvestigator> leadInvestigator;
    @JsonProperty("award-amount")
    public CrossrefAmount awardAmount;
    @JsonProperty("award-start")
    public CrossrefDate awardStart;
    @JsonProperty("award-end")
    public CrossrefDate awardEnd;
    public List<CrossrefFunding> funding;
}
