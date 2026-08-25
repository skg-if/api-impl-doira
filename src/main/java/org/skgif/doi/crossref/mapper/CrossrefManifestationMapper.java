package org.skgif.doi.crossref.mapper;

import static org.skgif.doi.util.SpotBugsSuppressions.NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE;
import static org.skgif.doi.util.SpotBugsSuppressions.SPOTBUGS_REGISTER;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
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

    /** Base URL Crossref's {@code type} controlled-vocabulary values are resolved against. */
    private static final String CROSSREF_TYPES_BASE_URL = "https://api.crossref.org/types/";

    /** Crossref {@code update-to[].type} value for a correction notice. */
    private static final String CROSSREF_UPDATE_TYPE_CORRECTION = "correction";
    /** Crossref {@code update-to[].type} value for a retraction notice. */
    private static final String CROSSREF_UPDATE_TYPE_RETRACTION = "retraction";

    // "correction"/"retraction" are the only type values Crossref's own docs give as examples
    // (no exhaustive enum is published) - any other value is ignored.
    /** Maps a Crossref {@code update-to[].type} value to its {@link ManifestationDateSetters} key. */
    private static final Map<String, String> CROSSREF_UPDATE_TYPE_TO_SKGIF = Map.of(
            CROSSREF_UPDATE_TYPE_CORRECTION, ManifestationDateSetters.CORRECTION,
            CROSSREF_UPDATE_TYPE_RETRACTION, ManifestationDateSetters.RETRACTION);

    /** Maps the record's publisher/container/page fields onto {@code Product.manifestations[].biblio}. */
    private final CrossrefBiblioMapper biblioMapper;

    CrossrefManifestationMapper(CrossrefBiblioMapper biblioMapper) {
        this.biblioMapper = biblioMapper;
    }

    ProductManifestation manifestation(CrossrefWork work, @Nullable CrossrefVenueMetadata venueMetadata) {
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

    @SuppressFBWarnings(value = NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE, justification = "work.updateTo() misread " +
            "as independently nullable per-call rather than a pure record accessor guarded by the preceding " +
            "null check - " + SPOTBUGS_REGISTER)
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

    private boolean addDateItem(ProductManifestationDates dates, @Nullable String type, @Nullable CrossrefDate date) {
        return date != null && ManifestationDateSetters.addDateItem(dates, type, date.toIsoDate().orElse(null));
    }

    private Optional<ProductManifestationAccessRights> accessRights(CrossrefWork work) {
        return LicenceMapper.accessRights(licenceUrls(work));
    }

    private Optional<String> licence(CrossrefWork work) {
        return LicenceMapper.licence(licenceUrls(work));
    }

    private List<String> licenceUrls(CrossrefWork work) {
        List<CrossrefLicense> licences = work.license();
        return licences == null ? List.of() : licences.stream().map(CrossrefLicense::url).toList();
    }
}
