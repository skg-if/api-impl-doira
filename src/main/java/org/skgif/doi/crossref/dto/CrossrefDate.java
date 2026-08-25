package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Crossref dates are structured {@code {date-parts: [[y,m,d]]}} objects, unlike DataCite's
 * plain ISO date strings - {@link #toIsoDate()} renders one down to the string form SKG-IF's
 * {@code ProductManifestationDates} lists expect. A partial date (year only, or year+month) is
 * valid Crossref input, so each part beyond the year is optional.
 *
 * @param dateParts the {@code [[year, month, day]]} parts, with month/day optional
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefDate(
        @JsonProperty("date-parts") @Nullable List<List<Integer>> dateParts) {

    /**
     * Renders this date's variable-length {@code date-parts} array as an ISO-8601 date string.
     *
     * @return this date's {@code date-parts} rendered as an ISO date string (year, year-month,
     *         or full date depending on how many parts are present), or Optional.empty() if
     *         dateParts is empty
     */
    public Optional<String> toIsoDate() {
        return firstValidParts().map(parts -> {
            StringBuilder iso = new StringBuilder(String.format("%04d", parts.getFirst()));
            appendMonthAndDay(iso, parts);
            return iso.toString();
        });
    }

    private Optional<List<Integer>> firstValidParts() {
        if (dateParts == null || dateParts.isEmpty()) {
            return Optional.empty();
        }
        List<Integer> parts = dateParts.getFirst();
        if (parts == null || parts.isEmpty() || parts.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(parts);
    }

    private static void appendMonthAndDay(StringBuilder iso, List<Integer> parts) {
        if (parts.size() <= 1 || parts.get(1) == null) {
            return;
        }
        iso.append('-').append(String.format("%02d", parts.get(1)));
        if (parts.size() > 2 && parts.get(2) != null) {
            iso.append('-').append(String.format("%02d", parts.get(2)));
        }
    }
}
