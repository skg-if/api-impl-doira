package org.skgif.doi.medra.mapper;

import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.ProductManifestationBiblio;
import org.skgif.doi.generated.model.ProductManifestationBiblioHostingDataSource;
import org.skgif.doi.generated.model.ProductManifestationBiblioIn;
import org.skgif.doi.generated.model.VenueLite;
import org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers;
import org.skgif.doi.medra.dto.MedraWork;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a mEDRA ONIX-for-DOI record's publisher/journal fields onto {@code
 * Product.manifestations[].biblio}. Split out of {@code MedraToSkgIfMapper} to keep that class
 * down to orchestration.
 */
final class MedraBiblioMapper {

    private MedraBiblioMapper() {
    }

    static ProductManifestationBiblio biblio(MedraWork work) {
        String hostingName = work.publisherName() != null ? work.publisherName() : work.registrantName();
        if (work.journalTitle() == null && hostingName == null) {
            return null;
        }
        ProductManifestationBiblio biblio = new ProductManifestationBiblio().in(venue(work));
        if (hostingName != null) {
            biblio.hostingDataSource(hostingDataSource(work.doi(), hostingName));
        }
        return biblio;
    }

    /**
     * mEDRA gives no journal-DOI equivalent to Crossref's {@code CrossrefJournalDoiResolver} - the
     * venue's {@code local_identifier} is always an otf id, backed only by the journal/series'
     * own ISSN(s) as its {@code identifiers[]}.
     *
     * @param work the mEDRA record to derive a venue from
     * @return the mapped Venue, or null if work.journalTitle() is null
     */
    private static ProductManifestationBiblioIn venue(MedraWork work) {
        if (work.journalTitle() == null) {
            return null;
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
        return venue;
    }

    /**
     * mEDRA's {@code PublisherName} (falling back to {@code RegistrantName}) has no external ID system.
     *
     * @param doi the owning record's DOI, used to build a deterministic otf id
     * @param name the publisher or registrant name
     * @return a DataSourceLite for name, with an otf local_identifier
     */
    private static ProductManifestationBiblioHostingDataSource hostingDataSource(String doi, String name) {
        return new DataSourceLite()
                .localIdentifier(MapperTextUtils.otf(doi, name))
                .entityType(DataSourceLite.EntityTypeEnum.DATASOURCE)
                .name(name);
    }
}
