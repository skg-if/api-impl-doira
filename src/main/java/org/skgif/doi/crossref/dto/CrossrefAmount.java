package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The {@code {amount, currency}} shape of a Crossref grant's {@code award-amount} - verified
 * live against a real Wellcome Trust grant record ({@code project[].award-amount} and {@code
 * project[].funding[].award-amount} both use this exact shape, the latter with an extra,
 * unused {@code percentage} field).
 *
 * @param amount   the award amount
 * @param currency the award amount's currency
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefAmount(
        Double amount,
        String currency) {
}
