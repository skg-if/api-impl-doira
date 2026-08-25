package org.skgif.doi.crossref.mapper;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.generated.model.ProductManifestationBiblio;
import org.skgif.doi.generated.model.ProductManifestationBiblioHostingDataSource;
import org.skgif.doi.generated.model.ProductManifestationBiblioIn;
import org.skgif.doi.generated.model.ProductManifestationBiblioPages;
import org.skgif.doi.generated.model.VenueLite;
import org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers;
import org.skgif.doi.spec.EntityTypes;
import org.skgif.doi.spec.IdentifierScheme;
import org.skgif.doi.util.EntityRefs;
import org.skgif.doi.util.LocalIdentifiers;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a Crossref work record's publisher/container/page fields onto {@code
 * Product.manifestations[].biblio}. Split out of {@code CrossrefToSkgIfMapper} to keep that class
 * down to orchestration. Needs {@link LocalIdentifiers} and {@link CrossrefJournalDoiResolver} to
 * resolve a real journal-level DOI for the venue, so - unlike the purely-static title/contribution
 * helpers - this is an instance collaborator, constructed once by the facade.
 */
final class CrossrefBiblioMapper {

    /** Number of parts a hyphen-split page range (e.g. {@code "12-34"}) splits into. */
    private static final int PAGE_RANGE_PARTS = 2;

    /** Builds full/otf local_identifier values for mapped entities. */
    private final LocalIdentifiers localIdentifiers;
    /** Resolves a real journal-level DOI for a journal venue, given its ISSN(s). */
    private final CrossrefJournalDoiResolver journalDoiResolver;

    CrossrefBiblioMapper(LocalIdentifiers localIdentifiers, CrossrefJournalDoiResolver journalDoiResolver) {
        this.localIdentifiers = localIdentifiers;
        this.journalDoiResolver = journalDoiResolver;
    }

    Optional<ProductManifestationBiblio> biblio(CrossrefWork work, @Nullable CrossrefVenueMetadata venueMetadata) {
        if (hasNoBiblioData(work, venueMetadata)) {
            return Optional.empty();
        }
        String volume = resolveVolume(work, venueMetadata).orElse(null);
        ProductManifestationBiblio biblio = new ProductManifestationBiblio()
                .issue(work.issue())
                .volume(volume)
                .pages(pages(work.page()).orElse(null))
                .in(venue(work, venueMetadata).orElse(null));
        if (work.publisher() != null) {
            biblio.hostingDataSource(hostingDataSource(work));
        }
        return Optional.of(biblio);
    }

    private boolean hasNoBiblioData(CrossrefWork work, @Nullable CrossrefVenueMetadata venueMetadata) {
        return work.publisher() == null && work.containerTitle() == null && work.issue() == null &&
                work.volume() == null && work.page() == null && venueMetadata == null;
    }

    // The REST JSON's `volume` is the product's own volume/issue number (e.g. a journal
    // volume); a book chapter's or proceedings paper's REST JSON commonly has neither that
    // nor a series volume, but the XML transform's `.../volume` (e.g. an LNCS series volume
    // number, or a recurring proceedings series volume) fills that gap when present.
    private Optional<String> resolveVolume(CrossrefWork work, @Nullable CrossrefVenueMetadata venueMetadata) {
        if (work.volume() != null) {
            return Optional.of(work.volume());
        }
        return Optional.ofNullable(venueMetadata != null ? venueMetadata.volume() : null);
    }

    private Optional<ProductManifestationBiblioPages> pages(@Nullable String page) {
        if (page == null || page.isBlank()) {
            return Optional.empty();
        }
        String[] parts = page.split("-", PAGE_RANGE_PARTS);
        ProductManifestationBiblioPages pages = new ProductManifestationBiblioPages().first(parts[0].trim());
        if (parts.length == PAGE_RANGE_PARTS) {
            pages.last(parts[1].trim());
        }
        return Optional.of(pages);
    }

    /**
     * A new capability vs. the DataCite mapper, which never populates {@code biblio.in} at all -
     * Crossref's {@code container-title}/{@code ISSN} give a clean SKG-IF Venue.
     *
     * <p>For chapter-in-a-book or paper-in-proceedings records, {@code container-title[]} can
     * hold more than one entry (e.g. a book or proceedings that's part of a series: {@code
     * ["<series name>", "<actual title>"]}) with no way to tell them apart from the REST JSON
     * alone - see {@code mapsVenueFromFirstContainerTitleEntryWhenNoBookMetadataAvailable}'s
     * golden-tested fallback below. When {@code venueMetadata} (parsed from Crossref's XML
     * transform endpoint - see {@code CrossrefVenueMetadataXmlParser}) is present, it takes
     * precedence: the container's own DOI becomes a real {@code local_identifier} (rather than an
     * otf id, when Crossref recorded one) and {@code identifiers[]} gains {@code doi} and {@code
     * isbn} entries alongside any series {@code issn}.
     *
     * <p>Otherwise (the plain journal-article case, or any other non-XML-enrichable type), {@link
     * CrossrefJournalDoiResolver} is tried: many journals themselves have a real Crossref {@code
     * type: "journal"} DOI, resolved live via their ISSN. When found, it's used the same way as
     * the XML-enriched container DOI above (real {@code local_identifier}, {@code doi} entry
     * alongside {@code issn}); when not (no journal-level DOI registered, or the lookup fails),
     * falls back to the {@code container-title[0]}+otf-id+ISSN-only heuristic.
     *
     * @param work          the Crossref work record to derive a venue from
     * @param venueMetadata venue metadata parsed from Crossref's XML transform endpoint, or null
     * @return the mapped Venue, or Optional.empty() if no container information is available
     */
    private Optional<ProductManifestationBiblioIn> venue(CrossrefWork work,
            @Nullable CrossrefVenueMetadata venueMetadata) {
        if (venueMetadata != null && venueMetadata.containerTitle() != null) {
            return Optional.of(venueFromXmlMetadata(work.doi(), venueMetadata));
        }
        if (hasNoContainerTitle(work)) {
            return Optional.empty();
        }
        // Non-null by the hasNoContainerTitle guard above; asserted here because that guard lives
        // in another method, where the nullness checker cannot follow it.
        String name = requireNonNull(work.containerTitle()).getFirst();
        List<String> issns = work.issn() != null ?
                work.issn().stream().filter(Objects::nonNull).toList() :
                List.of();
        String journalDoi = resolveJournalDoi(issns).orElse(null);

        VenueLite venue = new VenueLite()
                .localIdentifier(journalDoi != null ? localIdentifiers.toFullLocalIdentifier(journalDoi) :
                        MapperTextUtils.otf(work.doi(), name))
                .entityType(EntityTypes.VENUE.value())
                .name(name);

        List<VenueLiteAllOfIdentifiers> identifiers = journalArticleVenueIdentifiers(journalDoi, issns);
        if (!identifiers.isEmpty()) {
            venue.identifiers(identifiers);
        }
        return Optional.of(venue);
    }

    private boolean hasNoContainerTitle(CrossrefWork work) {
        return work.containerTitle() == null || work.containerTitle().isEmpty() ||
                work.containerTitle().getFirst() == null;
    }

    private Optional<String> resolveJournalDoi(List<String> issns) {
        return issns.isEmpty() ? Optional.empty() : journalDoiResolver.resolveJournalDoi(issns);
    }

    private List<VenueLiteAllOfIdentifiers> journalArticleVenueIdentifiers(@Nullable String journalDoi,
            List<String> issns) {
        List<VenueLiteAllOfIdentifiers> identifiers = new ArrayList<>();
        if (journalDoi != null) {
            identifiers.add(new VenueLiteAllOfIdentifiers().scheme(IdentifierScheme.DOI.value()).value(journalDoi));
        }
        issns.forEach(issn -> identifiers.add(
                new VenueLiteAllOfIdentifiers().scheme(IdentifierScheme.ISSN.value()).value(issn)));
        return identifiers;
    }

    private ProductManifestationBiblioIn venueFromXmlMetadata(@Nullable String doi,
            CrossrefVenueMetadata venueMetadata) {
        String containerDoi = venueMetadata.containerDoi();
        List<VenueLiteAllOfIdentifiers> identifiers = new ArrayList<>();
        if (containerDoi != null) {
            identifiers.add(new VenueLiteAllOfIdentifiers().scheme(IdentifierScheme.DOI.value()).value(containerDoi));
        }
        if (venueMetadata.seriesIssns() != null) {
            venueMetadata.seriesIssns().stream()
                    .filter(Objects::nonNull)
                    .forEach(issn -> identifiers.add(
                            new VenueLiteAllOfIdentifiers().scheme(IdentifierScheme.ISSN.value()).value(issn)));
        }
        if (venueMetadata.isbns() != null) {
            venueMetadata.isbns().stream()
                    .filter(Objects::nonNull)
                    .forEach(isbn -> identifiers.add(
                            new VenueLiteAllOfIdentifiers().scheme(IdentifierScheme.ISBN.value()).value(isbn)));
        }
        String name = venueMetadata.containerTitle();
        VenueLite venue = new VenueLite()
                .localIdentifier(containerDoi != null ? localIdentifiers.toFullLocalIdentifier(containerDoi) :
                        MapperTextUtils.otf(doi, name))
                .entityType(EntityTypes.VENUE.value())
                .name(name);
        if (!identifiers.isEmpty()) {
            venue.identifiers(identifiers);
        }
        return venue;
    }

    /**
     * Crossref's own {@code publisher} field is the closest generic equivalent of "where this
     * record is hosted" - same otf-id convention as DataCite's hostingDataSource.
     *
     * @param work the Crossref work record to derive a hosting data source from
     * @return a DataSourceLite for work.publisher, with an otf local_identifier
     */
    private ProductManifestationBiblioHostingDataSource hostingDataSource(CrossrefWork work) {
        return EntityRefs.hostingDataSource(work.doi(), work.publisher());
    }
}
