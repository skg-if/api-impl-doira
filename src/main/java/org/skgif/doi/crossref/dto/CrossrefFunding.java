package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefFunding(
        String scheme,
        @JsonProperty("award-amount") CrossrefAmount awardAmount,
        CrossrefFunder funder) {
}
