package org.skgif.doi.datacite.mapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDate;
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.util.ManifestationDateSetters;

/**
 * Maps a DataCite record's {@code dates[]} (plus system-generated fallback timestamps) onto
 * {@code Product.manifestations[].dates}. Split out of {@link DataCiteManifestationMapper} to keep
 * that class's method count down - date handling is a large, self-contained concern of its own.
 */
final class DataCiteManifestationDates {

    private static final int DAY_LENGTH = 10;

    /**
     * The {@code dateType} values documented in the DataCite Metadata Schema. DataCite has added
     * values to this list before and may do so again - {@link #fromValue(String)} returns {@link
     * Optional#empty()} rather than throwing for a value not yet in this enum, so a DataCite record
     * using a newer value is skipped like any other unmapped date instead of failing the mapping.
     */
    private enum DataCiteDateType {

        ACCEPTED("Accepted"),
        AVAILABLE("Available"),
        COLLECTED("Collected"),
        COPYRIGHTED("Copyrighted"),
        CREATED("Created"),
        ISSUED("Issued"),
        SUBMITTED("Submitted"),
        UPDATED("Updated"),
        VALID("Valid"),
        WITHDRAWN("Withdrawn");

        private static final Map<String, DataCiteDateType> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toMap(DataCiteDateType::value, Function.identity()));

        // Field intentionally shares its name with its accessor below, same idiom
        // CrossrefFilters.ParsedFilter.Builder's fields already do this for.
        @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
        private final String value;

        DataCiteDateType(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        static Optional<DataCiteDateType> fromValue(String value) {
            return Optional.ofNullable(BY_VALUE.get(value));
        }
    }

    private static final Map<DataCiteDateType, String> DATACITE_DATE_TYPE_TO_SKGIF = Map.of(
            DataCiteDateType.ACCEPTED, ManifestationDateSetters.ACCEPTANCE,
            DataCiteDateType.AVAILABLE, ManifestationDateSetters.EMBARGO,
            DataCiteDateType.COLLECTED, ManifestationDateSetters.COLLECTED,
            DataCiteDateType.COPYRIGHTED, ManifestationDateSetters.COPYRIGHT,
            DataCiteDateType.CREATED, ManifestationDateSetters.CREATION,
            DataCiteDateType.ISSUED, ManifestationDateSetters.PUBLICATION,
            DataCiteDateType.SUBMITTED, ManifestationDateSetters.DEPOSIT,
            DataCiteDateType.UPDATED, ManifestationDateSetters.MODIFIED,
            DataCiteDateType.VALID, ManifestationDateSetters.VALIDITY,
            DataCiteDateType.WITHDRAWN, ManifestationDateSetters.RETRACTION);

    private DataCiteManifestationDates() {
    }

    static Optional<ProductManifestationDates> dates(DataCiteAttributes attributes) {
        ProductManifestationDates dates = new ProductManifestationDates();
        boolean any = applyDatesArray(dates, attributes);
        any |= applyFallbackDates(dates, attributes);
        return any ? Optional.of(dates) : Optional.empty();
    }

    private static boolean applyDatesArray(ProductManifestationDates dates, DataCiteAttributes attributes) {
        if (attributes.dates() == null) {
            return false;
        }
        boolean any = false;
        for (DataCiteDate date : attributes.dates()) {
            String skgIfDateType = DataCiteDateType.fromValue(date.dateType())
                    .map(DATACITE_DATE_TYPE_TO_SKGIF::get)
                    .orElse(null);
            boolean missingMapping = skgIfDateType == null || date.date() == null;
            // An `Available` date only signals a genuine embargo when it differs (at day
            // granularity) from every other date already known for this record - if it
            // coincides with e.g. `Issued` or the top-level `created` timestamp, that's just
            // "published and immediately available," not an embargo end date, so it's dropped
            // rather than emitted anywhere.
            boolean redundantEmbargo = !missingMapping &&
                    ManifestationDateSetters.EMBARGO.equals(skgIfDateType) &&
                    otherRecordDays(attributes, date).contains(normalizeDay(date.date()));
            if (missingMapping || redundantEmbargo) {
                continue;
            }
            any |= ManifestationDateSetters.addDateItem(dates, skgIfDateType, date.date());
        }
        return any;
    }

    /**
     * Fall back to DataCite's system-generated record timestamps when the corresponding dateType
     * is absent from {@code dates[]} - which in practice is the norm, not the exception:
     * Created/Submitted/Updated essentially never appear there (see SKG_IF_DOI_MAPPING_DATES.md).
     * An explicit {@code dates[]} entry always takes precedence since these only fire when the
     * getter is still null.
     *
     * @param dates      the dates already populated from {@code attributes.dates[]}
     * @param attributes the DataCite record to read fallback timestamps from
     * @return true if any fallback timestamp was added
     */
    private static boolean applyFallbackDates(ProductManifestationDates dates, DataCiteAttributes attributes) {
        boolean any = false;
        if (dates.getCreation() == null && attributes.created() != null) {
            dates.addCreationItem(attributes.created());
            any = true;
        }
        if (dates.getDeposit() == null && attributes.registered() != null) {
            dates.addDepositItem(attributes.registered());
            any = true;
        }
        if (dates.getModified() == null && attributes.updated() != null) {
            dates.addModifiedItem(attributes.updated());
            any = true;
        }
        if (dates.getPublication() == null && attributes.published() != null) {
            dates.addPublicationItem(attributes.published());
            any = true;
        }
        return any;
    }

    /**
     * Day-normalized (YYYY-MM-DD) set of every other date already known for this record - the
     * rest of {@code attributes.dates} plus the top-level fallback timestamps - used to tell a
     * genuine embargo end date apart from an {@code Available} entry that merely restates another
     * date on the record.
     *
     * @param attributes the DataCite record to collect known dates from
     * @param excluding  the date entry to exclude from the result set
     * @return the day-normalized (YYYY-MM-DD) set of every other known date
     */
    // date != excluding intentionally checks reference identity to skip the one DataCiteDate
    // instance being excluded while iterating the same list it came from - .equals() would
    // wrongly also skip a different date entry that happens to carry the same value.
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private static Set<String> otherRecordDays(DataCiteAttributes attributes, DataCiteDate excluding) {
        Set<String> days = new HashSet<>();
        for (DataCiteDate date : attributes.dates()) {
            if (date != excluding && date.date() != null) {
                days.add(normalizeDay(date.date()));
            }
        }
        if (attributes.created() != null) {
            days.add(normalizeDay(attributes.created()));
        }
        if (attributes.registered() != null) {
            days.add(normalizeDay(attributes.registered()));
        }
        if (attributes.updated() != null) {
            days.add(normalizeDay(attributes.updated()));
        }
        if (attributes.published() != null) {
            days.add(normalizeDay(attributes.published()));
        }
        return days;
    }

    /**
     * Truncates a DataCite date string to its YYYY-MM-DD day, so a full timestamp
     * ({@code "2024-05-07T10:07:27.000Z"}) compares equal to a date-only value for the same day
     * ({@code "2024-05-07"}). Partial values (e.g. a bare year {@code "2028"}) are left as-is.
     *
     * @param date a DataCite date string, full timestamp or partial
     * @return the date truncated to its YYYY-MM-DD day, or unchanged if shorter than 10 chars
     */
    private static String normalizeDay(String date) {
        return date.length() >= DAY_LENGTH ? date.substring(0, DAY_LENGTH) : date;
    }
}
