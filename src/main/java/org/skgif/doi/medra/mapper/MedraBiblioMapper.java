package org.skgif.doi.medra.mapper;

import java.util.Optional;
import org.skgif.doi.generated.model.ProductManifestationBiblio;
import org.skgif.doi.generated.model.ProductManifestationBiblioIn;
import org.skgif.doi.generated.model.VenueLite;
import org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.util.EntityRefs;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a mEDRA ONIX-for-DOI record's publisher/journal fields onto {@code
 * Product.manifestations[].biblio}. Split out of {@code MedraToSkgIfMapper} to keep that class
 * down to orchestration.
 */
final class MedraBiblioMapper {

    private MedraBiblioMapper() {
    }

    static Optional<ProductManifestationBiblio> biblio(MedraWork work) {
        String hostingName = work.publisherName() != null ? work.publisherName() : work.registrantName();
        if (work.journalTitle() == null && hostingName == null) {
            return Optional.empty();
        }
        ProductManifestationBiblio biblio = new ProductManifestationBiblio().in(venue(work).orElse(null));
        if (hostingName != null) {
            biblio.hostingDataSource(EntityRefs.hostingDataSource(work.doi(), hostingName));
        }
        return Optional.of(biblio);
    }

    /**
     * mEDRA gives no journal-DOI equivalent to Crossref's {@code CrossrefJournalDoiResolver} - the
     * venue's {@code local_identifier} is always an otf id, backed only by the journal/series'
     * own ISSN(s) as its {@code identifiers[]}.
     *
     * @param work the mEDRA record to derive a venue from
     * @return the mapped Venue, or Optional.empty() if work.journalTitle() is null
     */
    private static Optional<ProductManifestationBiblioIn> venue(MedraWork work) {
        if (work.journalTitle() == null) {
            return Optional.empty();
        }
        VenueLite venue = new VenueLite()
                .localIdentifier(MapperTextUtils.otf(work.doi(), work.journalTitle()))
                .entityType(EntityTypes.VENUE)
                .name(work.journalTitle());
        if (work.issns() != null && !work.issns().isEmpty()) {
            venue.identifiers(work.issns().stream()
                    .map(issn -> new VenueLiteAllOfIdentifiers().scheme("issn").value(issn))
                    .toList());
        }
        return Optional.of(venue);
    }
}
