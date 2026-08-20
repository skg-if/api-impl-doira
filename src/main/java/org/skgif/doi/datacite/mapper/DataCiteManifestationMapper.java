package org.skgif.doi.datacite.mapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDate;
import org.skgif.doi.datacite.dto.DataCiteRights;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.generated.model.ProductManifestationType;
import org.skgif.doi.util.LicenceMapper;
import org.skgif.doi.util.ManifestationDateSetters;

/**
 * Maps a DataCite record's type/date/access-rights/licence fields onto {@code
 * Product.manifestations[]} (deferring the biblio portion to {@link DataCiteBiblioMapper}). Split
 * out of {@code DataCiteToSkgIfMapper} to keep that class down to orchestration.
 */
final class DataCiteManifestationMapper {

    private static final String DATACITE_RESOURCE_TYPE_SCHEMA_URL =
            "https://schema.datacite.org/meta/kernel-4.7/include/datacite-resourceType-v4.xsd";
    private static final int DAY_LENGTH = 10;

    private static final String DATACITE_ACCEPTED = "Accepted";
    private static final String DATACITE_AVAILABLE = "Available";
    private static final String DATACITE_COLLECTED = "Collected";
    private static final String DATACITE_COPYRIGHTED = "Copyrighted";
    private static final String DATACITE_CREATED = "Created";
    private static final String DATACITE_ISSUED = "Issued";
    private static final String DATACITE_SUBMITTED = "Submitted";
    private static final String DATACITE_UPDATED = "Updated";
    private static final String DATACITE_VALID = "Valid";
    private static final String DATACITE_WITHDRAWN = "Withdrawn";

    private static final Map<String, String> DATACITE_DATE_TYPE_TO_SKGIF = Map.of(
            DATACITE_ACCEPTED, ManifestationDateSetters.ACCEPTANCE,
            DATACITE_AVAILABLE, ManifestationDateSetters.EMBARGO,
            DATACITE_COLLECTED, ManifestationDateSetters.COLLECTED,
            DATACITE_COPYRIGHTED, ManifestationDateSetters.COPYRIGHT,
            DATACITE_CREATED, ManifestationDateSetters.CREATION,
            DATACITE_ISSUED, ManifestationDateSetters.PUBLICATION,
            DATACITE_SUBMITTED, ManifestationDateSetters.DEPOSIT,
            DATACITE_UPDATED, ManifestationDateSetters.MODIFIED,
            DATACITE_VALID, ManifestationDateSetters.VALIDITY,
            DATACITE_WITHDRAWN, ManifestationDateSetters.RETRACTION);

    private DataCiteManifestationMapper() {
    }

    static ProductManifestation manifestation(DataCiteAttributes attributes) {
        return new ProductManifestation()
                .type(manifestationType(attributes).orElse(null))
                .dates(dates(attributes).orElse(null))
                .accessRights(accessRights(attributes).orElse(null))
                .licence(licence(attributes).orElse(null))
                .version(attributes.version())
                .biblio(DataCiteBiblioMapper.biblio(attributes).orElse(null));
    }

    private static Optional<ProductManifestationType> manifestationType(DataCiteAttributes attributes) {
        return resourceTypeGeneral(attributes).map(resourceType -> new ProductManifestationType()
                .definedIn(DATACITE_RESOURCE_TYPE_SCHEMA_URL)
                .labels(Map.of("en", resourceType)));
    }

    static Optional<String> resourceTypeGeneral(DataCiteAttributes attributes) {
        return Optional.ofNullable(attributes.types() != null ? attributes.types().resourceTypeGeneral() : null);
    }

    private static Optional<ProductManifestationDates> dates(DataCiteAttributes attributes) {
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
            String skgIfDateType = DATACITE_DATE_TYPE_TO_SKGIF.get(date.dateType());
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

    private static Optional<ProductManifestationAccessRights> accessRights(DataCiteAttributes attributes) {
        return LicenceMapper.accessRights(licenceUrls(attributes));
    }

    private static Optional<String> licence(DataCiteAttributes attributes) {
        return LicenceMapper.licence(licenceUrls(attributes));
    }

    private static List<String> licenceUrls(DataCiteAttributes attributes) {
        return attributes.rightsList() == null ?
                List.of() :
                attributes.rightsList().stream().map(DataCiteRights::rightsUri).toList();
    }
}
