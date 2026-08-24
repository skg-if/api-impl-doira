package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One {@code project[].funding[]} entry of a Crossref grant record.
 *
 * @param scheme      the funding scheme name
 * @param awardAmount the awarded amount and its currency
 * @param funder      the funding organisation
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefFunding(
        String scheme,
        @JsonProperty("award-amount") CrossrefAmount awardAmount,
        CrossrefFunder funder) {
}
