package org.skgif.doi.util;

import java.util.Map;
import java.util.function.BiConsumer;
import org.skgif.doi.generated.model.ProductManifestationDates;

/**
 * SKG-IF date-type name (e.g. {@code "creation"}, {@code "publication"}) -&gt; {@link
 * ProductManifestationDates} setter dispatch, shared by every provider mapper that builds a {@code
 * dates} block from a per-item type/value pair ({@code CrossrefManifestationMapper}, {@code
 * DataCiteManifestationMapper}) - byte-identical dispatch logic that was previously duplicated
 * once per mapper class. Covers every {@code List<String>} date field on the generated model;
 * {@code request} is excluded since it's a scalar {@code String}, not a list, so it doesn't fit
 * this dispatch shape.
 */
public final class ManifestationDateSetters {

    public static final String ACCEPTANCE = "acceptance";
    public static final String ACCESS = "access";
    public static final String COLLECTED = "collected";
    public static final String COPYRIGHT = "copyright";
    public static final String CORRECTION = "correction";
    public static final String CREATION = "creation";
    public static final String DECISION = "decision";
    public static final String DEPOSIT = "deposit";
    public static final String DISTRIBUTION = "distribution";
    public static final String EMBARGO = "embargo";
    public static final String MODIFIED = "modified";
    public static final String PUBLICATION = "publication";
    public static final String RECEIVED = "received";
    public static final String RETRACTION = "retraction";
    public static final String VALIDITY = "validity";

    private static final Map<String, BiConsumer<ProductManifestationDates, String>> SETTERS = Map.ofEntries(
            Map.entry(ACCEPTANCE, ProductManifestationDates::addAcceptanceItem),
            Map.entry(ACCESS, ProductManifestationDates::addAccessItem),
            Map.entry(COLLECTED, ProductManifestationDates::addCollectedItem),
            Map.entry(COPYRIGHT, ProductManifestationDates::addCopyrightItem),
            Map.entry(CORRECTION, ProductManifestationDates::addCorrectionItem),
            Map.entry(CREATION, ProductManifestationDates::addCreationItem),
            Map.entry(DECISION, ProductManifestationDates::addDecisionItem),
            Map.entry(DEPOSIT, ProductManifestationDates::addDepositItem),
            Map.entry(DISTRIBUTION, ProductManifestationDates::addDistributionItem),
            Map.entry(EMBARGO, ProductManifestationDates::addEmbargoItem),
            Map.entry(MODIFIED, ProductManifestationDates::addModifiedItem),
            Map.entry(PUBLICATION, ProductManifestationDates::addPublicationItem),
            Map.entry(RECEIVED, ProductManifestationDates::addReceivedItem),
            Map.entry(RETRACTION, ProductManifestationDates::addRetractionItem),
            Map.entry(VALIDITY, ProductManifestationDates::addValidityItem));

    private ManifestationDateSetters() {
    }

    /**
     * @param dates the dates object to add to
     * @param skgIfDateType an SKG-IF date-type name (e.g. {@code "creation"}), or null
     * @param isoValue the ISO-8601-ish date string to add, or null
     * @return true if isoValue was non-null and skgIfDateType matched a known setter
     */
    public static boolean addDateItem(ProductManifestationDates dates, String skgIfDateType, String isoValue) {
        if (isoValue == null || skgIfDateType == null) {
            return false;
        }
        BiConsumer<ProductManifestationDates, String> setter = SETTERS.get(skgIfDateType);
        if (setter == null) {
            return false;
        }
        setter.accept(dates, isoValue);
        return true;
    }
}
