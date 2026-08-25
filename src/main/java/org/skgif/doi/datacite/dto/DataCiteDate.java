package org.skgif.doi.datacite.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * One {@code dates[]} entry, pairing a date value with its DataCite date type.
 *
 * @param date     the date value, which may be a year, year-month, full date or range
 * @param dateType DataCite's type for that date (e.g. {@code Issued})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataCiteDate(
        @Nullable String date,
        @Nullable String dateType) {
}
