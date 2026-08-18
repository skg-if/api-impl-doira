package org.skgif.doi.crossref.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadataXmlParser;
import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.VenueLite;
import org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossrefToSkgIfMapperVenueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Left entirely unstubbed by default: listWorks(...) returns null, so the resolver degrades
    // to Optional.empty() for every ISSN - every existing venue assertion below keeps exercising
    // today's container-title[0]+otf-id+ISSN-only fallback. See mapsVenueUsesRealJournalDoiWhen*
    // below for the resolver-hit path.
    private final CrossrefClient crossrefClient = mock(CrossrefClient.class);
    private final CrossrefToSkgIfMapper mapper = new CrossrefToSkgIfMapper(new LocalIdentifiers("https://doi.org/"),
            new CrossrefJournalDoiResolver(crossrefClient, Optional.empty()));

    private Product mapFixture(String resourceName) throws IOException {
        return mapper.toProduct(readFixture(resourceName));
    }

    private Product mapFixtureWithVenueMetadata(String jsonResourceName, String xmlResourceName) throws IOException {
        CrossrefVenueMetadata venueMetadata = CrossrefVenueMetadataXmlParser.parse(readXmlResource(xmlResourceName))
                .orElseThrow(() -> new AssertionError(
                        "Fixture XML did not parse to venue metadata: " + xmlResourceName));
        return mapper.toProduct(readFixture(jsonResourceName), venueMetadata);
    }

    private CrossrefWork readFixture(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            CrossrefWorkResponse response = objectMapper.readValue(in, CrossrefWorkResponse.class);
            return response.message();
        }
    }

    private String readXmlResource(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private CrossrefWorkListResponse journalDoiLookupResponse(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, CrossrefWorkListResponse.class);
        }
    }

    @Test
    void mapsBiblioFromContainerTitleIssnVolumeIssuePages() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        ProductManifestation manifestation = product.getManifestations().getFirst();
        VenueLite in = (VenueLite) manifestation.getBiblio().getIn();
        assertEquals("Nature", in.getName());
        assertEquals("issn", in.getIdentifiers().getFirst().getScheme());
        assertEquals("0028-0836", in.getIdentifiers().getFirst().getValue());
        assertEquals("500", manifestation.getBiblio().getVolume());
        assertEquals("7460", manifestation.getBiblio().getIssue());
        assertEquals("54", manifestation.getBiblio().getPages().getFirst());
        assertEquals("58", manifestation.getBiblio().getPages().getLast());
        assertEquals("Springer Science and Business Media LLC",
                ((DataSourceLite) manifestation.getBiblio().getHostingDataSource()).getName());
    }

    @Test
    void mapsVenueUsesRealJournalDoiWhenCrossrefResolverFindsOne() throws IOException {
        // ISSN 0028-0836 (this fixture's print ISSN) resolves live to Nature's own real Crossref
        // journal-type DOI - verified against https://api.crossref.org/works?filter=type:journal
        // ,issn:0028-0836 - see crossref-journal-doi-lookup-nature.json for the raw response.
        when(crossrefClient.listWorks(eq("type:journal,issn:0028-0836"), any(), any(), eq(1), any(), any()))
                .thenReturn(journalDoiLookupResponse("crossref-journal-doi-lookup-nature.json"));

        Product product = mapFixture("crossref-journal-article.json");

        VenueLite venue = (VenueLite) product.getManifestations().getFirst().getBiblio().getIn();
        assertEquals("Nature", venue.getName());
        assertEquals("https://doi.org/10.1038/41586.1476-4687", venue.getLocalIdentifier());

        List<VenueLiteAllOfIdentifiers> identifiers = venue.getIdentifiers();
        final int expectedIdentifierCount = 3;
        assertEquals(expectedIdentifierCount, identifiers.size());
        assertEquals("doi", identifiers.getFirst().getScheme());
        assertEquals("10.1038/41586.1476-4687", identifiers.getFirst().getValue());
        assertTrue(identifiers.stream()
                .anyMatch(i -> "issn".equals(i.getScheme()) && "0028-0836".equals(i.getValue())));
        assertTrue(identifiers.stream()
                .anyMatch(i -> "issn".equals(i.getScheme()) && "1476-4687".equals(i.getValue())));
    }

    @Test
    void mapsVenueFallsBackToOtfIdWhenJournalDoiLookupFails() throws IOException {
        // Same ISSN as above, but this time the Crossref lookup itself blows up (network error,
        // timeout, etc.) - the venue must still come out exactly as today's container-title[0]
        // +otf-id+ISSN-only heuristic, not fail the whole product mapping.
        when(crossrefClient.listWorks(eq("type:journal,issn:0028-0836"), any(), any(), eq(1), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        Product product = mapFixture("crossref-journal-article.json");

        VenueLite venue = (VenueLite) product.getManifestations().getFirst().getBiblio().getIn();
        assertTrue(venue.getLocalIdentifier().startsWith("otf___"));
        assertEquals("issn", venue.getIdentifiers().getFirst().getScheme());
        assertEquals("0028-0836", venue.getIdentifiers().getFirst().getValue());
    }

    @Test
    void doesNotFabricatePagesWhenOnlyArticleNumberIsPresent() throws IOException {
        // This fixture has no "page" field (Nature Communications uses "article-number"
        // instead, which the mapper has no SKG-IF field to carry) - pages must stay unset
        // rather than being guessed at, even though issue/volume/venue are all present.
        Product product = mapFixture("crossref-journal-article-with-orcid.json");

        ProductManifestation manifestation = product.getManifestations().getFirst();
        assertNull(manifestation.getBiblio().getPages());
        assertEquals("13", manifestation.getBiblio().getVolume());
        assertEquals("1", manifestation.getBiblio().getIssue());
        assertEquals("Nature Communications", ((VenueLite) manifestation.getBiblio().getIn()).getName());
    }

    @Test
    void mapsVenueFromFirstContainerTitleEntryWhenNoVenueMetadataAvailable() throws IOException {
        // container-title is ["Lecture Notes in Computer Science", "Cryptographic Hardware and
        // Embedded Systems - CHES 2017"] - with no venue metadata parsed from Crossref's XML
        // transform (the 1-arg mapFixture() passes null), the mapper falls back to index 0, so
        // the venue ends up named after the series, not the actual proceedings/book. See
        // mapsVenueFromXmlMetadataForBookInSeries below for the corrected, enriched path.
        Product product = mapFixture("crossref-book-chapter.json");

        assertEquals("Lecture Notes in Computer Science",
                ((VenueLite) product.getManifestations().getFirst().getBiblio().getIn()).getName());
    }

    @Test
    void mapsVenueFromXmlMetadataForBookInSeries() throws IOException {
        // crossref-book-chapter.xml is Crossref's XML transform for the same DOI - a book that's
        // part of a series (book_series_metadata). Once available, it takes precedence over the
        // ambiguous container-title[] array: correct book title, the book's own DOI as a real
        // local_identifier (not an otf id), doi/issn/isbn identifiers together, and the series
        // volume number (absent from this fixture's REST JSON) filling in biblio.volume.
        Product product = mapFixtureWithVenueMetadata("crossref-book-chapter.json", "crossref-book-chapter.xml");

        VenueLite venue = (VenueLite) product.getManifestations().getFirst().getBiblio().getIn();
        assertEquals("Cryptographic Hardware and Embedded Systems – CHES 2017", venue.getName());
        assertEquals("https://doi.org/10.1007/978-3-319-66787-4", venue.getLocalIdentifier());

        List<VenueLiteAllOfIdentifiers> identifiers = venue.getIdentifiers();
        final int expectedIdentifierCount = 5;
        assertEquals(expectedIdentifierCount, identifiers.size());
        assertEquals("doi", identifiers.getFirst().getScheme());
        assertEquals("10.1007/978-3-319-66787-4", identifiers.getFirst().getValue());
        assertTrue(identifiers.stream()
                .anyMatch(i -> "issn".equals(i.getScheme()) && "0302-9743".equals(i.getValue())));
        assertTrue(identifiers.stream()
                .anyMatch(i -> "issn".equals(i.getScheme()) && "1611-3349".equals(i.getValue())));
        assertTrue(identifiers.stream()
                .anyMatch(i -> "isbn".equals(i.getScheme()) && "978-3-319-66786-7".equals(i.getValue())));
        assertTrue(identifiers.stream()
                .anyMatch(i -> "isbn".equals(i.getScheme()) && "978-3-319-66787-4".equals(i.getValue())));

        assertEquals("10529", product.getManifestations().getFirst().getBiblio().getVolume());
    }

    @Test
    void mapsVenueFromXmlMetadataForStandaloneBookWithoutSeries() throws IOException {
        // crossref-book-chapter-standalone.json/.xml: a book chapter whose book (Apress' "The
        // Definitive Guide to Jakarta Faces...") isn't part of any series - book_metadata, not
        // book_series_metadata. Proves the no-series path: book title/DOI/ISBN still enrich the
        // venue, but there's no series ISSN and no volume number to add.
        Product product = mapFixtureWithVenueMetadata("crossref-book-chapter-standalone.json",
                "crossref-book-chapter-standalone.xml");

        VenueLite venue = (VenueLite) product.getManifestations().getFirst().getBiblio().getIn();
        assertEquals("The Definitive Guide to Jakarta Faces in Jakarta EE 10", venue.getName());
        assertEquals("https://doi.org/10.1007/978-1-4842-7310-4", venue.getLocalIdentifier());

        List<VenueLiteAllOfIdentifiers> identifiers = venue.getIdentifiers();
        final int expectedIdentifierCount = 3;
        assertEquals(expectedIdentifierCount, identifiers.size());
        assertEquals("doi", identifiers.getFirst().getScheme());
        assertEquals("10.1007/978-1-4842-7310-4", identifiers.getFirst().getValue());
        assertTrue(identifiers.stream().noneMatch(i -> "issn".equals(i.getScheme())));
        assertTrue(identifiers.stream()
                .anyMatch(i -> "isbn".equals(i.getScheme()) && "978-1-4842-7309-8".equals(i.getValue())));
        assertTrue(identifiers.stream()
                .anyMatch(i -> "isbn".equals(i.getScheme()) && "978-1-4842-7310-4".equals(i.getValue())));

        assertNull(product.getManifestations().getFirst().getBiblio().getVolume());
    }

    @Test
    void mapsVenueFromXmlMetadataForProceedingsInSeries() throws IOException {
        // crossref-proceedings-article-with-series.json/.xml: a real proceedings-article (DOI
        // 10.2991/assehr.k.211222.032, ICIRAD 2021) whose container-title[] has the exact same
        // series-vs-actual-title ambiguity as the book case: ["Advances in Social Science,
        // Education and Humanities Research" (series), "Proceedings of the 4th International
        // Conference..." (the actual proceedings)]. Crossref's proceedings_series_metadata here
        // has no doi_data at all - proves the otf-id local_identifier fallback for a case the
        // book fixtures never exercised (both of those had a container DOI).
        Product product = mapFixtureWithVenueMetadata("crossref-proceedings-article-with-series.json",
                "crossref-proceedings-article-with-series.xml");

        VenueLite venue = (VenueLite) product.getManifestations().getFirst().getBiblio().getIn();
        assertEquals(
                "Proceedings of the 4th International Conference on Innovative Research Across Disciplines " +
                        "(ICIRAD 2021)",
                venue.getName());
        assertTrue(venue.getLocalIdentifier().startsWith("otf___"));

        List<VenueLiteAllOfIdentifiers> identifiers = venue.getIdentifiers();
        assertEquals(2, identifiers.size());
        assertTrue(identifiers.stream().noneMatch(i -> "doi".equals(i.getScheme())));
        assertTrue(identifiers.stream()
                .anyMatch(i -> "issn".equals(i.getScheme()) && "2352-5398".equals(i.getValue())));
        assertTrue(identifiers.stream()
                .anyMatch(i -> "isbn".equals(i.getScheme()) && "978-94-6239-490-2".equals(i.getValue())));

        assertEquals("613", product.getManifestations().getFirst().getBiblio().getVolume());
    }

    @Test
    void mapsVenueFromXmlMetadataForStandaloneProceedingsWithoutSeries() throws IOException {
        // crossref-proceedings-article-standalone.json/.xml: the user's own example DOI
        // (10.1109/freq.1998.717994, IEEE) - proceedings_metadata, no series wrapper. Its REST
        // JSON container-title[0] already happens to match the XML's proceedings_title, but its
        // ISBN is entirely absent from the REST JSON - proves the XML-only ISBN enrichment path
        // for proceedings, mirroring the book standalone case.
        Product product = mapFixtureWithVenueMetadata("crossref-proceedings-article-standalone.json",
                "crossref-proceedings-article-standalone.xml");

        VenueLite venue = (VenueLite) product.getManifestations().getFirst().getBiblio().getIn();
        assertEquals("Proceedings of the 1998 IEEE International Frequency Control Symposium (Cat. No.98CH36165)",
                venue.getName());
        assertTrue(venue.getLocalIdentifier().startsWith("otf___"));

        List<VenueLiteAllOfIdentifiers> identifiers = venue.getIdentifiers();
        assertEquals(1, identifiers.size());
        assertEquals("isbn", identifiers.getFirst().getScheme());
        assertEquals("0-7803-4373-5", identifiers.getFirst().getValue());

        assertNull(product.getManifestations().getFirst().getBiblio().getVolume());
    }
}
