package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefInvestigator {

    public String given;
    public String family;
    @JsonProperty("ORCID")
    public String orcid;
    public List<CrossrefAffiliation> affiliation;
}
