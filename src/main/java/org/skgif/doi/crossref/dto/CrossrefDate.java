package org.skgif.doi.crossref.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Crossref dates are structured {@code {date-parts: [[y,m,d]]}} objects, unlike DataCite's
 * plain ISO date strings - {@link #toIsoDate()} renders one down to the string form SKG-IF's
 * {@code ProductManifestationDates} lists expect. A partial date (year only, or year+month) is
 * valid Crossref input, so each part beyond the year is optional.
 *
 * @param dateParts the {@code [[year, month, day]]} parts, with month/day optional
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CrossrefDate(@JsonProperty("date-parts") List<List<Integer>> dateParts) {

    /**
     * @return this date's {@code date-parts} rendered as an ISO date string (year, year-month,
     *     or full date depending on how many parts are present), or null if dateParts is empty
     */
    public String toIsoDate() {
        if (dateParts == null || dateParts.isEmpty()) {
            return null;
        }
        List<Integer> parts = dateParts.get(0);
        if (parts == null || parts.isEmpty() || parts.get(0) == null) {
            return null;
        }
        StringBuilder iso = new StringBuilder(String.format("%04d", parts.get(0)));
        if (parts.size() > 1 && parts.get(1) != null) {
            iso.append('-').append(String.format("%02d", parts.get(1)));
            if (parts.size() > 2 && parts.get(2) != null) {
                iso.append('-').append(String.format("%02d", parts.get(2)));
            }
        }
        return iso.toString();
    }
}
