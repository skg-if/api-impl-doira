package org.skgif.doi.crossref.mapper;

import java.util.Map;
import java.util.function.BiConsumer;
import org.skgif.doi.crossref.dto.CrossrefDate;
import org.skgif.doi.crossref.dto.CrossrefLicense;
import org.skgif.doi.crossref.dto.CrossrefUpdateTo;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.generated.model.ProductManifestationType;

/**
 * Maps a Crossref work record's type/date/access-rights/licence fields onto {@code
 * Product.manifestations[]} (deferring the biblio/venue portion to {@link CrossrefBiblioMapper}).
 * Split out of {@code CrossrefToSkgIfMapper} to keep that class down to orchestration.
 */
final class CrossrefManifestationMapper {

    private static final String CROSSREF_TYPES_BASE_URL = "https://api.crossref.org/types/";
    private static final String DATE_TYPE_PUBLICATION = "publication";
    private static final String DATE_TYPE_CORRECTION = "correction";
    private static final String DATE_TYPE_RETRACTION = "retraction";

    private static final Map<String, BiConsumer<ProductManifestationDates, String>> DATE_SETTERS = Map.of(
            "creation", ProductManifestationDates::addCreationItem,
            "deposit", ProductManifestationDates::addDepositItem,
            "modified", ProductManifestationDates::addModifiedItem,
            "acceptance", ProductManifestationDates::addAcceptanceItem,
            DATE_TYPE_PUBLICATION, ProductManifestationDates::addPublicationItem,
            DATE_TYPE_CORRECTION, ProductManifestationDates::addCorrectionItem,
            DATE_TYPE_RETRACTION, ProductManifestationDates::addRetractionItem);

    private final CrossrefBiblioMapper biblioMapper;

    CrossrefManifestationMapper(CrossrefBiblioMapper biblioMapper) {
        this.biblioMapper = biblioMapper;
    }

    ProductManifestation manifestation(CrossrefWork work, CrossrefVenueMetadata venueMetadata) {
        return new ProductManifestation()
                .type(manifestationType(work))
                .dates(dates(work))
                .accessRights(accessRights(work))
                .licence(licence(work))
                .biblio(biblioMapper.biblio(work, venueMetadata));
    }

    private ProductManifestationType manifestationType(CrossrefWork work) {
        return work.type != null
                ? new ProductManifestationType()
                        .propertyClass(CROSSREF_TYPES_BASE_URL + work.type)
                        .definedIn(CROSSREF_TYPES_BASE_URL)
                        .labels(Map.of("en", work.type))
                : null;
    }

    private ProductManifestationDates dates(CrossrefWork work) {
        ProductManifestationDates dates = new ProductManifestationDates();
        boolean any = false;
        any |= addDateItem(dates, "creation", work.created);
        any |= addDateItem(dates, "deposit", work.deposited);
        // Crossref documents `deposited` as "date on which the work metadata was most recently
        // updated" - that's SKG-IF's `modified`, not just `deposit`, and Crossref has no other
        // candidate for `modified` (`indexed` is deliberately excluded - see the mapping doc).
        any |= addDateItem(dates, "modified", work.deposited);
        any |= addDateItem(dates, "acceptance", work.accepted);
        any |= addDateItem(dates, DATE_TYPE_PUBLICATION, work.publishedPrint);
        any |= addDateItem(dates, DATE_TYPE_PUBLICATION, work.publishedOnline);
        any |= addDateItem(dates, DATE_TYPE_PUBLICATION, work.issued);
        if (work.updateTo != null) {
            for (CrossrefUpdateTo update : work.updateTo) {
                // "correction"/"retraction" are the only type values Crossref's own docs give
                // as examples (no exhaustive enum is published) - any other value is ignored.
                if (DATE_TYPE_CORRECTION.equals(update.type)) {
                    any |= addDateItem(dates, DATE_TYPE_CORRECTION, update.updated);
                } else if (DATE_TYPE_RETRACTION.equals(update.type)) {
                    any |= addDateItem(dates, DATE_TYPE_RETRACTION, update.updated);
                }
            }
        }
        return any ? dates : null;
    }

    private boolean addDateItem(ProductManifestationDates dates, String type, CrossrefDate date) {
        if (date == null) {
            return false;
        }
        String iso = date.toIsoDate();
        if (iso == null) {
            return false;
        }
        BiConsumer<ProductManifestationDates, String> setter = DATE_SETTERS.get(type);
        if (setter == null) {
            return false;
        }
        setter.accept(dates, iso);
        return true;
    }

    private ProductManifestationAccessRights accessRights(CrossrefWork work) {
        if (work.license == null || work.license.isEmpty()) {
            return null;
        }
        boolean open = work.license.stream().anyMatch(this::isOpenLicence);
        return new ProductManifestationAccessRights()
                .status(open ? ProductManifestationAccessRights.StatusEnum.OPEN : null);
    }

    private boolean isOpenLicence(CrossrefLicense licence) {
        return licence.url != null && licence.url.contains("creativecommons.org");
    }

    private String licence(CrossrefWork work) {
        if (work.license == null || work.license.isEmpty()) {
            return null;
        }
        return work.license.get(0).url;
    }
}
