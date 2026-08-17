package org.skgif.doi.crossref.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefJournalDoiResolver;
import org.skgif.doi.crossref.dto.CrossrefIdEntry;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadata;
import org.skgif.doi.crossref.xml.CrossrefVenueMetadataXmlParser;
import org.skgif.doi.generated.model.DataSourceLite;
import org.skgif.doi.generated.model.Grant;
import org.skgif.doi.generated.model.GrantContribution;
import org.skgif.doi.generated.model.GrantLite;
import org.skgif.doi.generated.model.Organisation;
import org.skgif.doi.generated.model.PersonLite;
import org.skgif.doi.generated.model.Product;
import org.skgif.doi.generated.model.ProductContribution;
import org.skgif.doi.generated.model.ProductManifestation;
import org.skgif.doi.generated.model.ProductManifestationAccessRights;
import org.skgif.doi.generated.model.ProductsRelatedCitesInner;
import org.skgif.doi.generated.model.ProductsRelatedItem;
import org.skgif.doi.generated.model.VenueLite;
import org.skgif.doi.generated.model.VenueLiteAllOfIdentifiers;
import org.skgif.doi.util.LocalIdentifiers;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossrefToSkgIfMapperTest {

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

    private Grant mapGrantFixture(String resourceName) throws IOException {
        return mapper.toGrant(readFixture(resourceName));
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

    @Test
    void mapsCoreFieldsFromRealJournalArticle() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertEquals("https://doi.org/10.1038/nature12373", product.getLocalIdentifier());
        assertEquals("product", product.getEntityType());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals(1, product.getIdentifiers().size());
        assertEquals("doi", product.getIdentifiers().getFirst().getScheme());
        assertEquals("10.1038/nature12373", product.getIdentifiers().getFirst().getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsTitles() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        Map<String, List<String>> titles = (Map<String, List<String>>) product.getTitles();
        assertTrue(titles.get("en").getFirst().contains("Nanometre-scale thermometry"));
    }

    @Test
    void mapsResourceTypeToProductType() throws IOException {
        assertEquals(Product.ProductTypeEnum.LITERATURE, mapFixture("crossref-journal-article.json").getProductType());
        assertEquals(Product.ProductTypeEnum.RESEARCH_DATA, mapFixture("crossref-dataset.json").getProductType());
    }

    @Test
    void mapsAuthorsAsAuthorContributionsWithoutOrcidWhenAbsent() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertFalse(product.getContributions().isEmpty());
        ProductContribution first = product.getContributions().getFirst();
        assertEquals(ProductContribution.RoleEnum.AUTHOR, first.getRole());
        assertEquals(1, first.getRank());
        PersonLite by = (PersonLite) first.getBy();
        assertEquals("G.", by.getGivenName());
        assertEquals("Kucsko", by.getFamilyName());
        // This fixture's authors carry no ORCID - local_identifier falls back to an otf id.
        assertTrue(by.getLocalIdentifier().startsWith("otf___"));
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

    private CrossrefWorkListResponse journalDoiLookupResponse(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, CrossrefWorkListResponse.class);
        }
    }

    @Test
    void mapsRelatedProductsFromReferenceListDois() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        assertFalse(product.getRelatedProducts().getCites().isEmpty());
        boolean hasKnownReference = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> "https://doi.org/10.1038/nature03509"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier()));
        assertTrue(hasKnownReference);
    }

    @Test
    void mapsReferenceWithoutDoiToOtfIdInsteadOfExcludingIt() throws IOException {
        Product product = mapFixture("crossref-journal-article.json");

        // This fixture's reference[] has 30 entries, one of which (key BFnature12373_CR17)
        // carries no DOI - it must still surface as a cites entry, via an otf id, not be dropped.
        final int expectedReferenceCount = 30;
        assertEquals(expectedReferenceCount, product.getRelatedProducts().getCites().size());
        boolean hasOtfReference = product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> ((ProductsRelatedItem) c).getLocalIdentifier().startsWith("otf___"));
        assertTrue(hasOtfReference);
    }

    // crossref-journal-article-with-is-supplemented-by.json: a real IUCrData article (DOI
    // 10.1107/s2414314618016334) whose relation map carries 4 real is-supplemented-by entries
    // (all DOI-shaped supplement-file identifiers), alongside a normal reference[] - the first
    // fixture to exercise related_products.is_supplemented_by for Crossref.

    @Test
    void mapsIsSupplementedByFromRelationMap() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-is-supplemented-by.json");

        List<ProductsRelatedCitesInner> isSupplementedBy = product.getRelatedProducts().getIsSupplementedBy();
        final int expectedIsSupplementedByCount = 4;
        assertEquals(expectedIsSupplementedByCount, isSupplementedBy.size());
        ProductsRelatedItem first = (ProductsRelatedItem) isSupplementedBy.getFirst();
        assertEquals("https://doi.org/10.1107/S2414314618016334/lh4040sup1.cif", first.getLocalIdentifier());
        assertEquals("doi", first.getIdentifiers().getFirst().getScheme());
        assertEquals("10.1107/S2414314618016334/lh4040sup1.cif", first.getIdentifiers().getFirst().getValue());

        // Same fixture also has a normal reference[] - adding is_supplemented_by must not
        // disturb cites.
        assertFalse(product.getRelatedProducts().getCites().isEmpty());
    }

    @Test
    void mapsIsSupplementedByToOtfIdWhenRelationEntryIsNotDoiShaped() throws IOException {
        // No real fixture is expected to carry a non-DOI is-supplemented-by entry - mutated in
        // Java from the real fixture's own DOI-shaped entry to prove the otf fallback still works.
        CrossrefWork work = readFixture("crossref-journal-article-with-is-supplemented-by.json");
        List<CrossrefIdEntry> supplements = work.relation().get("is-supplemented-by");
        CrossrefIdEntry original = supplements.getFirst();
        supplements.set(0, new CrossrefIdEntry(original.id(), "handle", original.assertedBy()));

        Product product = mapper.toProduct(work);

        ProductsRelatedItem item = (ProductsRelatedItem) product.getRelatedProducts().getIsSupplementedBy().getFirst();
        assertTrue(item.getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void doesNotFabricateManifestationVersion() throws IOException {
        // Crossref has no software-versioning concept - left unset rather than guessed at.
        Product product = mapFixture("crossref-journal-article.json");

        assertNull(product.getManifestations().getFirst().getVersion());
    }

    // crossref-journal-article-with-orcid.json: a real Nature Communications article (DOI
    // 10.1038/s41467-022-33468-6) - unlike crossref-journal-article.json, its authors carry
    // ORCIDs and it has no "page" field (only "article-number"), so it exercises paths the
    // other journal-article fixtures don't.

    @Test
    void mapsCoreFieldsFromRealArticleWithOrcidAuthors() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-orcid.json");

        assertEquals("https://doi.org/10.1038/s41467-022-33468-6", product.getLocalIdentifier());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals("10.1038/s41467-022-33468-6", product.getIdentifiers().getFirst().getValue());
    }

    @Test
    void mapsAuthorsAsAuthorContributionsWithOrcidWhenPresent() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-orcid.json");

        ProductContribution first = product.getContributions().getFirst();
        PersonLite by = (PersonLite) first.getBy();
        assertEquals("M. C.", by.getGivenName());
        assertEquals("Rahn", by.getFamilyName());
        assertEquals("https://orcid.org/0000-0001-7403-8288", by.getLocalIdentifier());
        assertEquals("orcid", by.getIdentifiers().getFirst().getScheme());
        assertEquals("0000-0001-7403-8288", by.getIdentifiers().getFirst().getValue());

        // This fixture also has authors without an ORCID (e.g. "A. Hariki") interspersed among
        // the ORCID-bearing ones - those must still fall back to an otf id, not be skipped.
        boolean hasOtfAuthor = product.getContributions().stream()
                .anyMatch(c -> ((PersonLite) c.getBy()).getLocalIdentifier().startsWith("otf___"));
        assertTrue(hasOtfAuthor);
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

    // crossref-proceedings-article.json: a real conference-proceedings record (DOI
    // 10.17537/icmbb18.42, type: "proceedings-article") - no license, no funder, and one
    // reference (key "ref3") with neither a DOI nor unstructured text, exercising the
    // otf-id-from-key fallback that the other journal-article fixtures never hit.

    @Test
    void mapsCoreFieldsFromRealProceedingsArticle() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        assertEquals("https://doi.org/10.17537/icmbb18.42", product.getLocalIdentifier());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals("10.17537/icmbb18.42", product.getIdentifiers().getFirst().getValue());
    }

    @Test
    void mapsReferenceWithNeitherDoiNorUnstructuredToOtfIdFromKey() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        // reference key "ref3" carries no DOI and no unstructured text - the otf id must
        // fall back to the reference key itself rather than being dropped.
        final int expectedCitesCount = 5;
        assertEquals(expectedCitesCount, product.getRelatedProducts().getCites().size());
        assertTrue(product.getRelatedProducts().getCites().stream()
                .anyMatch(c -> "otf___10-17537-icmbb18-42___ref3"
                        .equals(((ProductsRelatedItem) c).getLocalIdentifier())));
    }

    @Test
    void doesNotFabricateAccessRightsWhenNoLicensePresent() throws IOException {
        Product product = mapFixture("crossref-proceedings-article.json");

        assertNull(product.getManifestations().getFirst().getAccessRights());
        assertNull(product.getManifestations().getFirst().getLicence());
    }

    // crossref-journal-article-with-ror-affiliation.json: a real Physical Review B article
    // (DOI 10.1103/physrevb.110.174515) whose author affiliations carry a ROR directly - unlike
    // every other journal-article fixture (name-only affiliations, or none at all). It also has
    // a funder with its own Funder Registry DOI and the same funder repeated with two different
    // award numbers, neither of which the other fixtures exercise.

    @Test
    void mapsDeclaredAffiliationsWithRorWhenPresent() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-ror-affiliation.json");

        ProductContribution first = product.getContributions().getFirst();
        assertEquals("di Mauro", ((PersonLite) first.getBy()).getFamilyName());
        // This fixture's authors carry no ORCID at all - only their affiliations carry a ROR -
        // so the person's own local_identifier still falls back to an otf id.
        assertTrue(((PersonLite) first.getBy()).getLocalIdentifier().startsWith("otf___"));

        Organisation affiliation = (Organisation) first.getDeclaredAffiliations().getFirst();
        assertEquals(2, first.getDeclaredAffiliations().size());
        assertEquals("https://ror.org/00tmb7y09", affiliation.getLocalIdentifier());
        assertEquals("ror", affiliation.getIdentifiers().getFirst().getScheme());
        assertEquals("00tmb7y09", affiliation.getIdentifiers().getFirst().getValue());
        assertEquals("Laboratoire de Chimie Théorique", affiliation.getName());
    }

    @Test
    void mapsFundingWithFunderDoiAndMultipleAwardsForSameFunder() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-ror-affiliation.json");

        // 4 funder entries in the fixture, two of which are the same "Horizon 2020" funder with
        // two different award numbers - each award must surface as its own funding entry.
        final int expectedFundingCount = 4;
        assertEquals(expectedFundingCount, product.getFunding().size());
        List<GrantLite> horizon2020Entries = product.getFunding().stream()
                .map(f -> (GrantLite) f)
                .filter(f -> "Horizon 2020".equals(f.getFundingAgency().getName()))
                .toList();
        assertEquals(2, horizon2020Entries.size());
        assertTrue(horizon2020Entries.stream().anyMatch(f -> "810367".equals(f.getGrantNumber())));
        assertTrue(horizon2020Entries.stream().anyMatch(f -> "802533".equals(f.getGrantNumber())));

        // Unlike crossref-journal-article-with-funder.json's funder (no Funder Registry DOI at
        // all), this fixture's funders carry one directly on the top-level funder[] entry.
        var fundingAgency = horizon2020Entries.getFirst().getFundingAgency();
        assertEquals("doi", fundingAgency.getIdentifiers().getFirst().getScheme());
        assertEquals("10.13039/501100007601", fundingAgency.getIdentifiers().getFirst().getValue());
    }

    // crossref-book-chapter.json: a real book chapter (DOI 10.1007/978-3-319-66787-4_9,
    // type: "book-chapter") - unlike every other fixture, its container-title[] has two
    // entries (the LNCS series title, then the actual proceedings/book title), and it's the
    // first fixture to exercise the mapper's new publisher-as-contribution behaviour.

    @Test
    void mapsCoreFieldsFromRealBookChapter() throws IOException {
        Product product = mapFixture("crossref-book-chapter.json");

        assertEquals("https://doi.org/10.1007/978-3-319-66787-4_9", product.getLocalIdentifier());
        assertEquals(Product.ProductTypeEnum.LITERATURE, product.getProductType());
        assertEquals("10.1007/978-3-319-66787-4_9", product.getIdentifiers().getFirst().getValue());
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
                "Proceedings of the 4th International Conference on Innovative Research Across Disciplines "
                        + "(ICIRAD 2021)",
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

    @Test
    void mapsPublisherAsTrailingPublisherRoleContribution() throws IOException {
        Product product = mapFixture("crossref-book-chapter.json");

        // 6 authors, so the publisher contribution the mapper now appends must be the 7th,
        // ranked after every author.
        final int authorCount = 6;
        List<ProductContribution> contributions = product.getContributions();
        assertEquals(authorCount + 1, contributions.size());
        ProductContribution publisherContribution = contributions.get(authorCount);
        assertEquals(ProductContribution.RoleEnum.PUBLISHER, publisherContribution.getRole());
        assertEquals(authorCount + 1, publisherContribution.getRank());
        Organisation publisherBy = (Organisation) publisherContribution.getBy();
        assertEquals("Springer International Publishing", publisherBy.getName());
        assertTrue(publisherBy.getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void mapsAbstractStrippingJatsXmlTags() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        Map<String, List<String>> abstracts = (Map<String, List<String>>) product.getAbstracts();
        String abstractText = abstracts.get("en").getFirst();
        assertTrue(abstractText.contains("Lissajous scanner"));
        assertFalse(abstractText.contains("<jats:p>"));
    }

    @Test
    void mapsAccessRightsAsOpenFromCreativeCommonsLicence() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        ProductManifestation manifestation = product.getManifestations().getFirst();
        assertEquals(ProductManifestationAccessRights.StatusEnum.OPEN, manifestation.getAccessRights().getStatus());
        assertTrue(manifestation.getLicence().contains("creativecommons.org"));
    }

    @Test
    void mapsFunderWithoutAwardNumberOrFunderDoi() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        assertEquals(1, product.getFunding().size());
        GrantLite grant = (GrantLite) product.getFunding().getFirst();
        assertEquals("Federal Ministries of Transport, Innovation and Technology", grant.getFundingAgency().getName());
        // No Funder Registry DOI on this fixture's funder - local_identifier falls back to otf.
        assertNull(grant.getFundingAgency().getIdentifiers());
        assertTrue(grant.getFundingAgency().getLocalIdentifier().startsWith("otf___"));
    }

    @Test
    void mapsDeclaredAffiliationsWithNameOnlyOtfFallbackWhenNoRor() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-funder.json");

        var affiliations = product.getContributions().getFirst().getDeclaredAffiliations();
        assertEquals(1, affiliations.size());
        Organisation affiliation = (Organisation) affiliations.getFirst();
        // Unlike crossref-journal-article-with-ror-affiliation.json, this affiliation carries no
        // ROR at all - only a bare name - so it must fall back to an otf id instead.
        assertEquals("Carinthian Tech Research AG, Europastrasse 12, 9524 Villach, Austria",
                affiliation.getName());
        assertNull(affiliation.getIdentifiers());
        assertTrue(affiliation.getLocalIdentifier().startsWith("otf___"));
    }

    // crossref-grant.json: a real Crossref grant record (type: "grant") - a Wellcome Trust
    // award, with explicit funder/amount/duration fields Crossref gives directly (unlike
    // DataCite Awards, which need the ROR-bearing-creator heuristic).

    @Test
    void toGrant_mapsCoreFieldsFromRealGrantRecord() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertEquals("https://doi.org/10.35802/218300", grant.getLocalIdentifier());
        assertEquals("grant", grant.getEntityType().toString());
        assertEquals(1, grant.getIdentifiers().size());
        assertEquals("doi", grant.getIdentifiers().getFirst().getScheme());
        assertEquals("10.35802/218300", grant.getIdentifiers().getFirst().getValue());
        assertEquals("218300", grant.getGrantNumber());
    }

    @Test
    @SuppressWarnings("unchecked")
    void toGrant_mapsTitlesAndAbstractsFromProject() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        Map<String, String> titles = (Map<String, String>) grant.getTitles();
        assertTrue(titles.get("en").contains("Biocontainment Level 2"));

        // Two project-description entries in the fixture, concatenated into one string since
        // Grant.abstracts (unlike Product.abstracts) is a plain string per language.
        Map<String, String> abstracts = (Map<String, String>) grant.getAbstracts();
        assertTrue(abstracts.get("en").contains("Provision of cutting edge cell-sorter"));
        assertTrue(abstracts.get("en").contains("Our bodies are composed of trillions"));
    }

    @Test
    void toGrant_mapsFundingAgencyAmountCurrencyAndDurationExplicitly() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertEquals("Wellcome Trust", grant.getFundingAgency().getName());
        assertEquals("doi", grant.getFundingAgency().getIdentifiers().getFirst().getScheme());
        assertEquals("10.13039/100010269", grant.getFundingAgency().getIdentifiers().getFirst().getValue());
        final int expectedFundedAmount = 479450;
        assertEquals(expectedFundedAmount, grant.getFundedAmount());
        assertEquals("GBP", grant.getCurrency());
        assertEquals("2019-11-01", grant.getDuration().getStart());
        assertEquals("2024-10-31", grant.getDuration().getEnd());
    }

    @Test
    void toGrant_mapsLeadAndCoInvestigatorsAsContributionsWithRoles() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        final int expectedInvestigatorCount = 9;
        assertEquals(expectedInvestigatorCount, grant.getContributions().size());
        GrantContribution lead = (GrantContribution) grant.getContributions().stream()
                .filter(c -> "Halim".equals(((PersonLite) ((GrantContribution) c).getBy()).getFamilyName()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(GrantContribution.RolesEnum.LEAD_APPLICANT), lead.getRoles());
        PersonLite leadBy = (PersonLite) lead.getBy();
        assertEquals("orcid", leadBy.getIdentifiers().getFirst().getScheme());
        assertEquals("0000-0001-9773-0023", leadBy.getIdentifiers().getFirst().getValue());

        GrantContribution coApplicant = (GrantContribution) grant.getContributions().stream()
                .filter(c -> "Caldas".equals(((PersonLite) ((GrantContribution) c).getBy()).getFamilyName()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(GrantContribution.RolesEnum.CO_APPLICANT), coApplicant.getRoles());
        Organisation coApplicantAffiliation = (Organisation) coApplicant.getDeclaredAffiliations().getFirst();
        assertEquals("ror", coApplicantAffiliation.getIdentifiers().getFirst().getScheme());
        assertEquals("013meh722", coApplicantAffiliation.getIdentifiers().getFirst().getValue());
    }

    @Test
    void toGrant_dedupesBeneficiariesFromInvestigatorAffiliations() throws IOException {
        Grant grant = mapGrantFixture("crossref-grant.json");

        // 3 distinct institutions appear across 9 investigators (University of Cambridge
        // repeats 6 times) - beneficiaries must be deduped by name.
        final int expectedDistinctInstitutionCount = 3;
        assertEquals(expectedDistinctInstitutionCount, grant.getBeneficiaries().size());
        assertTrue(grant.getBeneficiaries().stream()
                .anyMatch(b -> "University of Cambridge".equals(((Organisation) b).getName())));
    }

    @Test
    void toGrant_doesNotFabricateAcronym() throws IOException {
        // No Crossref grant-schema field found for this - left unset rather than guessed at.
        Grant grant = mapGrantFixture("crossref-grant.json");

        assertNull(grant.getAcronym());
    }

    @Test
    void mapsDepositedIntoBothDepositAndModified() throws IOException {
        // Crossref documents `deposited` as "date on which the work metadata was most recently
        // updated" - the same field feeds both SKG-IF dates, not just `deposit`.
        Product product = mapFixture("crossref-journal-article.json");

        var dates = product.getManifestations().getFirst().getDates();
        assertEquals(List.of("2023-05-18"), dates.getDeposit());
        assertEquals(List.of("2023-05-18"), dates.getModified());
    }

    @Test
    void mapsUpdateToCorrectionAndRetractionDates() throws IOException {
        Product product = mapFixture("crossref-journal-article-with-update-to.json");

        var dates = product.getManifestations().getFirst().getDates();
        assertEquals(List.of("2021-03-05"), dates.getCorrection());
        assertEquals(List.of("2022-06-20"), dates.getRetraction());
    }

    @Test
    void ignoresUnrecognizedUpdateToType() throws IOException {
        // The fixture also carries an "erratum" entry - Crossref's update-to[].type isn't
        // exhaustively documented (only "correction"/"retraction" are confirmed), so any other
        // value must be ignored rather than guessed at.
        Product product = mapFixture("crossref-journal-article-with-update-to.json");

        var dates = product.getManifestations().getFirst().getDates();
        assertEquals(1, dates.getCorrection().size());
        assertEquals(1, dates.getRetraction().size());
    }
}
