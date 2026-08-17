package org.skgif.doi.datacite.mapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDate;
import org.skgif.doi.datacite.dto.DataCiteRights;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.generated.model.ProductManifestationType;

/**
 * Maps a DataCite record's type/date/access-rights/licence fields onto {@code
 * Product.manifestations[]} (deferring the biblio portion to {@link DataCiteBiblioMapper}). Split
 * out of {@code DataCiteToSkgIfMapper} to keep that class down to orchestration.
 */
final class DataCiteManifestationMapper {

    private static final String DATACITE_RESOURCE_TYPE_SCHEMA_URL =
            "https://schema.datacite.org/meta/kernel-4.7/include/datacite-resourceType-v4.xsd";
    private static final int DAY_LENGTH = 10;

    private static final Map<String, String> DATACITE_DATE_TYPE_TO_SKGIF = Map.of(
            "Accepted", "acceptance",
            "Available", "embargo",
            "Collected", "collected",
            "Copyrighted", "copyright",
            "Created", "creation",
            "Issued", "publication",
            "Submitted", "deposit",
            "Updated", "modified",
            "Valid", "validity",
            "Withdrawn", "retraction");

    private static final Map<String, BiConsumer<ProductManifestationDates, String>> DATE_SETTERS = Map.ofEntries(
            Map.entry("acceptance", ProductManifestationDates::addAcceptanceItem),
            Map.entry("collected", ProductManifestationDates::addCollectedItem),
            Map.entry("copyright", ProductManifestationDates::addCopyrightItem),
            Map.entry("creation", ProductManifestationDates::addCreationItem),
            Map.entry("publication", ProductManifestationDates::addPublicationItem),
            Map.entry("deposit", ProductManifestationDates::addDepositItem),
            Map.entry("modified", ProductManifestationDates::addModifiedItem),
            Map.entry("validity", ProductManifestationDates::addValidityItem),
            Map.entry("retraction", ProductManifestationDates::addRetractionItem),
            Map.entry("embargo", ProductManifestationDates::addEmbargoItem));

    private DataCiteManifestationMapper() {
    }

    static ProductManifestation manifestation(DataCiteAttributes attributes) {
        return new ProductManifestation()
                .type(manifestationType(attributes))
                .dates(dates(attributes))
                .accessRights(accessRights(attributes))
                .licence(licence(attributes))
                .version(attributes.version)
                .biblio(DataCiteBiblioMapper.biblio(attributes));
    }

    private static ProductManifestationType manifestationType(DataCiteAttributes attributes) {
        String resourceType = resourceTypeGeneral(attributes);
        if (resourceType == null) {
            return null;
        }
        return new ProductManifestationType()
                .definedIn(DATACITE_RESOURCE_TYPE_SCHEMA_URL)
                .labels(Map.of("en", resourceType));
    }

    static String resourceTypeGeneral(DataCiteAttributes attributes) {
        return attributes.types != null ? attributes.types.resourceTypeGeneral : null;
    }

    private static ProductManifestationDates dates(DataCiteAttributes attributes) {
        ProductManifestationDates dates = new ProductManifestationDates();
        boolean any = applyDatesArray(dates, attributes);
        any |= applyFallbackDates(dates, attributes);
        return any ? dates : null;
    }

    private static boolean applyDatesArray(ProductManifestationDates dates, DataCiteAttributes attributes) {
        if (attributes.dates == null) {
            return false;
        }
        boolean any = false;
        for (DataCiteDate date : attributes.dates) {
            String skgIfDateType = DATACITE_DATE_TYPE_TO_SKGIF.get(date.dateType);
            if (skgIfDateType == null || date.date == null) {
                continue;
            }
            // An `Available` date only signals a genuine embargo when it differs (at day
            // granularity) from every other date already known for this record - if it
            // coincides with e.g. `Issued` or the top-level `created` timestamp, that's just
            // "published and immediately available," not an embargo end date, so it's dropped
            // rather than emitted anywhere.
            if ("embargo".equals(skgIfDateType)
                    && otherRecordDays(attributes, date).contains(normalizeDay(date.date))) {
                continue;
            }
            BiConsumer<ProductManifestationDates, String> setter = DATE_SETTERS.get(skgIfDateType);
            if (setter == null) {
                continue;
            }
            setter.accept(dates, date.date);
            any = true;
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
     * @param dates the dates already populated from {@code attributes.dates[]}
     * @param attributes the DataCite record to read fallback timestamps from
     * @return true if any fallback timestamp was added
     */
    private static boolean applyFallbackDates(ProductManifestationDates dates, DataCiteAttributes attributes) {
        boolean any = false;
        if (dates.getCreation() == null && attributes.created != null) {
            dates.addCreationItem(attributes.created);
            any = true;
        }
        if (dates.getDeposit() == null && attributes.registered != null) {
            dates.addDepositItem(attributes.registered);
            any = true;
        }
        if (dates.getModified() == null && attributes.updated != null) {
            dates.addModifiedItem(attributes.updated);
            any = true;
        }
        if (dates.getPublication() == null && attributes.published != null) {
            dates.addPublicationItem(attributes.published);
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
     * @param excluding the date entry to exclude from the result set
     * @return the day-normalized (YYYY-MM-DD) set of every other known date
     */
    private static Set<String> otherRecordDays(DataCiteAttributes attributes, DataCiteDate excluding) {
        Set<String> days = new HashSet<>();
        for (DataCiteDate date : attributes.dates) {
            if (date != excluding && date.date != null) {
                days.add(normalizeDay(date.date));
            }
        }
        if (attributes.created != null) {
            days.add(normalizeDay(attributes.created));
        }
        if (attributes.registered != null) {
            days.add(normalizeDay(attributes.registered));
        }
        if (attributes.updated != null) {
            days.add(normalizeDay(attributes.updated));
        }
        if (attributes.published != null) {
            days.add(normalizeDay(attributes.published));
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

    private static ProductManifestationAccessRights accessRights(DataCiteAttributes attributes) {
        if (attributes.rightsList == null || attributes.rightsList.isEmpty()) {
            return null;
        }
        boolean open = attributes.rightsList.stream().anyMatch(DataCiteManifestationMapper::isOpenLicence);
        return new ProductManifestationAccessRights()
                .status(open ? ProductManifestationAccessRights.StatusEnum.OPEN : null);
    }

    private static boolean isOpenLicence(DataCiteRights rights) {
        return rights.rightsUri != null && rights.rightsUri.contains("creativecommons.org");
    }

    private static String licence(DataCiteAttributes attributes) {
        if (attributes.rightsList == null || attributes.rightsList.isEmpty()) {
            return null;
        }
        return attributes.rightsList.get(0).rightsUri;
    }
}
