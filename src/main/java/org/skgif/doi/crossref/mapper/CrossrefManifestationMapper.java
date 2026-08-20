package org.skgif.doi.crossref.mapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.skgif.doi.crossref.dto.CrossrefDate;
import org.skgif.doi.crossref.dto.CrossrefLicense;
import org.skgif.doi.crossref.dto.CrossrefUpdateTo;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductManifestationDates;
import org.skgif.doi.generated.model.ProductManifestationType;
import org.skgif.doi.util.LicenceMapper;
import org.skgif.doi.util.ManifestationDateSetters;

/**
 * Maps a Crossref work record's type/date/access-rights/licence fields onto {@code
 * Product.manifestations[]} (deferring the biblio/venue portion to {@link CrossrefBiblioMapper}).
 * Split out of {@code CrossrefToSkgIfMapper} to keep that class down to orchestration.
 */
final class CrossrefManifestationMapper {

    private static final String CROSSREF_TYPES_BASE_URL = "https://api.crossref.org/types/";

    private static final String CROSSREF_UPDATE_TYPE_CORRECTION = "correction";
    private static final String CROSSREF_UPDATE_TYPE_RETRACTION = "retraction";

    // "correction"/"retraction" are the only type values Crossref's own docs give as examples
    // (no exhaustive enum is published) - any other value is ignored.
    private static final Map<String, String> CROSSREF_UPDATE_TYPE_TO_SKGIF = Map.of(
            CROSSREF_UPDATE_TYPE_CORRECTION, ManifestationDateSetters.CORRECTION,
            CROSSREF_UPDATE_TYPE_RETRACTION, ManifestationDateSetters.RETRACTION);

    private final CrossrefBiblioMapper biblioMapper;

    CrossrefManifestationMapper(CrossrefBiblioMapper biblioMapper) {
        this.biblioMapper = biblioMapper;
    }

    ProductManifestation manifestation(CrossrefWork work, CrossrefVenueMetadata venueMetadata) {
        return new ProductManifestation()
                .type(manifestationType(work).orElse(null))
                .dates(dates(work).orElse(null))
                .accessRights(accessRights(work).orElse(null))
                .licence(licence(work).orElse(null))
                .biblio(biblioMapper.biblio(work, venueMetadata).orElse(null));
    }

    private Optional<ProductManifestationType> manifestationType(CrossrefWork work) {
        return Optional.ofNullable(work.type() != null ?
                new ProductManifestationType()
                        .propertyClass(CROSSREF_TYPES_BASE_URL + work.type())
                        .definedIn(CROSSREF_TYPES_BASE_URL)
                        .labels(Map.of("en", work.type())) :
                null);
    }

    private Optional<ProductManifestationDates> dates(CrossrefWork work) {
        ProductManifestationDates dates = new ProductManifestationDates();
        boolean any = false;
        any |= addDateItem(dates, ManifestationDateSetters.CREATION, work.created());
        any |= addDateItem(dates, ManifestationDateSetters.DEPOSIT, work.deposited());
        // Crossref documents `deposited` as "date on which the work metadata was most recently
        // updated" - that's SKG-IF's `modified`, not just `deposit`, and Crossref has no other
        // candidate for `modified` (`indexed` is deliberately excluded - see the mapping doc).
        any |= addDateItem(dates, ManifestationDateSetters.MODIFIED, work.deposited());
        any |= addDateItem(dates, ManifestationDateSetters.ACCEPTANCE, work.accepted());
        any |= addDateItem(dates, ManifestationDateSetters.PUBLICATION, work.publishedPrint());
        any |= addDateItem(dates, ManifestationDateSetters.PUBLICATION, work.publishedOnline());
        any |= addDateItem(dates, ManifestationDateSetters.PUBLICATION, work.issued());
        if (work.updateTo() != null) {
            for (CrossrefUpdateTo update : work.updateTo()) {
                String skgIfType = CROSSREF_UPDATE_TYPE_TO_SKGIF.get(update.type());
                any |= addDateItem(dates, skgIfType, update.updated());
            }
        }
        return any ? Optional.of(dates) : Optional.empty();
    }

    private boolean addDateItem(ProductManifestationDates dates, String type, CrossrefDate date) {
        return date != null && ManifestationDateSetters.addDateItem(dates, type, date.toIsoDate().orElse(null));
    }

    private Optional<ProductManifestationAccessRights> accessRights(CrossrefWork work) {
        return LicenceMapper.accessRights(licenceUrls(work));
    }

    private Optional<String> licence(CrossrefWork work) {
        return LicenceMapper.licence(licenceUrls(work));
    }

    private List<String> licenceUrls(CrossrefWork work) {
        return work.license() == null ? List.of() : work.license().stream().map(CrossrefLicense::url).toList();
    }
}
