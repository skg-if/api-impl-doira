package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossrefFunding {

    public String scheme;
    @JsonProperty("award-amount")
    public CrossrefAmount awardAmount;
    public CrossrefFunder funder;
}
