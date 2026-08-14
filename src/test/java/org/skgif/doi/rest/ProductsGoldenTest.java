package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefXmlTransformClient;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import org.skgif.doi.medra.MedraClient;

/**
 * Full JSON-LD output regression tests for {@code /datacite/products}, {@code
 * /crossref/products}, and {@code /medra/products}: the actual response body must exactly match
 * (structurally - key order doesn't matter) the corresponding checked-in document under {@code
 * src/test/resources/expected/}. These committed documents double as a live, readable reference
 * for what this API actually produces for a real record.
 *
 * <p>Regenerate the golden JSON-LD files instead of asserting against them - e.g. after an
 * intentional mapper change: {@code mvn test -Dtest=ProductsGoldenTest -Dgolden.regenerate=true}
 * Then {@code git diff} the {@code expected/} files to review exactly what changed before
 * committing.
 */
@QuarkusTest
class ProductsGoldenTest {

    private static final String BASE = "/skg-if/api";

    private static final boolean REGENERATE_GOLDEN = Boolean.getBoolean("golden.regenerate");

    @InjectMock
    @RestClient
    DataCiteClient dataCiteClient;

    @InjectMock
    @RestClient
    CrossrefClient crossrefClient;

    @InjectMock
    @RestClient
    CrossrefXmlTransformClient crossrefXmlTransformClient;

    @InjectMock
    @RestClient
    MedraClient medraClient;

    private DataCiteDoiResponse loadDataCiteFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, DataCiteDoiResponse.class);
        }
    }

    private CrossrefWorkResponse loadCrossrefFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, CrossrefWorkResponse.class);
        }
    }

    private CrossrefWorkListResponse loadCrossrefWorkListFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, CrossrefWorkListResponse.class);
        }
    }

    private String loadRawResource(String resourceName) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * A 200 {@link Response} mock wrapping the given XML fixture's raw content. Must be built as
     * its own statement, assigned to a local variable, before being passed to {@code
     * when(...).thenReturn(...)} elsewhere - see {@code CrossrefProductsResourceTest}'s copy of
     * this helper for why inlining it corrupts Mockito's stubbing state.
     */
    private Response okXmlResponse(String xmlResourceName) throws IOException {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(String.class)).thenReturn(loadRawResource(xmlResourceName));
        return response;
    }

    @Test
    void getProductById_matchesExpectedJsonLd_esrfDc2493599001() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.15151/esrf-dc-2493599001", "datacite-esrf-dc-2493599001.json",
                "expected/datacite-esrf-dc-2493599001-out.json");
    }

    @Test
    void getProductById_matchesExpectedJsonLd_esrfEs2210534378_withAffiliationsAndFunding() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.15151/esrf-es-2210534378", "datacite-esrf-es-2210534378.json",
                "expected/datacite-esrf-es-2210534378-out.json");
    }

    /**
     * DOI 10.5281/zenodo.21826016 - a real Zenodo software deposit ({@code
     * resourceTypeGeneral: "Software"}), proving the {@code research software} product-type
     * mapping at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_zenodoSoftware21826016() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.5281/zenodo.21826016", "datacite-zenodo-software-21826016.json",
                "expected/datacite-zenodo-software-21826016-out.json");
    }

    /**
     * DOI 10.5281/zenodo.20750072 - a real Zenodo text deposit ({@code
     * resourceTypeGeneral: "Text"}), proving the {@code literature} product-type mapping via
     * DataCite at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_zenodoText20750072() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.5281/zenodo.20750072", "datacite-zenodo-text-20750072.json",
                "expected/datacite-zenodo-text-20750072-out.json");
    }

    /**
     * DOI 10.5281/zenodo.21232199 - a real Zenodo deposit whose contributor carries
     * contributorType "Editor", proving the editor-role contribution mapping at the
     * golden-output level (see {@code DataCiteToSkgIfMapperTest.mapsEditorContributorTypeToEditorRole}).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_zenodoEditor21232199() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.5281/zenodo.21232199", "datacite-zenodo-editor-21232199.json",
                "expected/datacite-zenodo-editor-21232199-out.json");
    }

    /**
     * DOI 10.5281/zenodo.21914195 - a real Zenodo deposit whose relatedIdentifiers mix both
     * DataCite citation-like relation types ("Cites" and "References"), proving both feed
     * related_products.cites at the golden-output level (see {@code
     * DataCiteToSkgIfMapperTest.mapsBothCitesAndReferencesRelationTypesIntoTheSameCitesArray}).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_zenodoCitesReferences21914195() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.5281/zenodo.21914195", "datacite-zenodo-cites-references-21914195.json",
                "expected/datacite-zenodo-cites-references-21914195-out.json");
    }

    /**
     * DOI 10.5281/zenodo.21827103 - a real Zenodo dataset whose relatedIdentifiers exercise
     * "IsSupplementedBy", "IsDocumentedBy", "IsNewVersionOf", and "IsPartOf" - each landing in
     * its own related_products field - alongside a decoy "IsSupplementTo" and an unmodeled
     * "HasVersion" (see {@code DataCiteToSkgIfMapperTest.mapsIsSupplementedByIsDocumentedByAndIsNewVersionOf}
     * et al.).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_zenodoRelations21827103() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.5281/zenodo.21827103", "datacite-zenodo-relations-21827103.json",
                "expected/datacite-zenodo-relations-21827103-out.json");
    }

    /**
     * DOI 10.82227/repository.uwtsd.ac.uk.00004342 - a real UWTSD repository thesis whose
     * funding reference identifies the funder via "Crossref Funder ID" rather than "ROR",
     * proving the otf fallback for `funding[].funding_agency` at the golden-output level (see
     * {@code DataCiteToSkgIfMapperTest.mapsFundingAgencyToOtfWhenFunderIdentifierTypeIsNotRor}).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_uwtsdThesis4342() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.82227/repository.uwtsd.ac.uk.00004342",
                "datacite-thesis-crossref-funder-id-4342.json", "expected/datacite-thesis-crossref-funder-id-4342-out.json");
    }

    /**
     * DOI 10.17630/e449e75a-1ee9-4490-909c-e3913052cce1 - a real University of St Andrews
     * dataset whose 3 funding references carry no {@code funderIdentifier} at all, proving the
     * otf fallback for `funding[].funding_agency` at the golden-output level when the identifier
     * is entirely absent (rather than merely non-ROR/non-DOI-shaped - see
     * {@code DataCiteToSkgIfMapperTest.mapsFundingAgencyToOtfWhenFunderIdentifierIsEntirelyAbsent}).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_standrewsDatasetFunderNoIdentifier() throws IOException {
        assertMatchesExpectedDataCiteJsonLd("10.17630/e449e75a-1ee9-4490-909c-e3913052cce1",
                "datacite-dataset-funder-no-identifier-e449e75a.json",
                "expected/datacite-dataset-funder-no-identifier-e449e75a-out.json");
    }

    /**
     * Full JSON-LD regression test for the DataCite search/list endpoint with multiple,
     * heterogeneous @graph items and full pagination metadata (both prev_page and next_page
     * present, unlike a single-item, single-page response). Reuses the two DOI fixtures already
     * exercised by the single-entity golden tests above, so the two mapped products are
     * known-good and no new DataCite network capture is needed. Page 2 of 3 is the deliberate
     * choice: it's the one scenario where both prev_page and next_page are emitted
     * simultaneously.
     */
    @Test
    void getProducts_matchesExpectedJsonLd_multipleItemsPage2Of3() throws IOException {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse();
        listResponse.data = java.util.List.of(
                loadDataCiteFixture("datacite-esrf-dc-2493599001.json").data,
                loadDataCiteFixture("datacite-esrf-es-2210534378.json").data);
        DataCiteDoiListResponse.Meta meta = new DataCiteDoiListResponse.Meta();
        meta.total = 5;
        meta.totalPages = 3;
        meta.page = 2;
        listResponse.meta = meta;

        when(dataCiteClient.listDois(any(), any(), anyInt(), anyInt())).thenReturn(listResponse);

        String actualBody = given()
                .when().get(BASE + "/datacite/products?page=2&page_size=2")
                .then()
                .statusCode(200)
                .extract().asString();

        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), "expected/datacite-products-search-multiple-out.json");
    }

    @Test
    void getProductById_matchesExpectedJsonLd_natureArticle() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.1038/nature12373", "crossref-journal-article.json",
                "expected/crossref-journal-article-out.json");
    }

    /**
     * Same DOI as above, but with Nature's ISSN (0028-0836) resolving live to a real Crossref
     * {@code type: "journal"} DOI ({@code 10.1038/41586.1476-4687} - see {@code
     * crossref-journal-doi-lookup-nature.json}, captured from the real API) via {@code
     * CrossrefJournalDoiResolver} - proves the venue's real-DOI {@code local_identifier} (instead
     * of an otf id) and combined doi/issn identifiers at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_natureArticleWithJournalDoi() throws IOException {
        when(crossrefClient.getWork(eq("10.1038/nature12373")))
                .thenReturn(loadCrossrefFixture("crossref-journal-article.json"));
        when(crossrefClient.listWorks(eq("type:journal,issn:0028-0836"), any(), any(), eq(1), any(), any()))
                .thenReturn(loadCrossrefWorkListFixture("crossref-journal-doi-lookup-nature.json"));

        String actualBody = given()
                .when().get(BASE + "/crossref/products/10.1038/nature12373")
                .then()
                .statusCode(200)
                .extract().asString();

        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), "expected/crossref-journal-article-with-journal-doi-out.json");
    }

    /**
     * Same golden-regression pattern as above, for DOI 10.1038/s41467-022-33468-6 (ORCID-bearing
     * authors, no "page" field).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_orcidArticle() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.1038/s41467-022-33468-6", "crossref-journal-article-with-orcid.json",
                "expected/crossref-journal-article-with-orcid-out.json");
    }

    /**
     * Same golden-regression pattern as above, for DOI 10.17537/icmbb18.42 (a
     * "proceedings-article" with no license/funder).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_proceedingsArticle() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.17537/icmbb18.42", "crossref-proceedings-article.json",
                "expected/crossref-proceedings-article-out.json");
    }

    /**
     * Same golden-regression pattern as above, for DOI 10.1103/physrevb.110.174515 (ROR-tagged
     * author affiliations, a funder with its own Funder Registry DOI).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_rorAffiliationArticle() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.1103/physrevb.110.174515", "crossref-journal-article-with-ror-affiliation.json",
                "expected/crossref-journal-article-with-ror-affiliation-out.json");
    }

    /**
     * DOI 10.17989/encsr154xia - a real Crossref {@code type: "dataset"} record, proving the
     * {@code research data} product-type mapping via Crossref at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_datasetArticle() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.17989/encsr154xia", "crossref-dataset.json",
                "expected/crossref-dataset-out.json");
    }

    /**
     * DOI 10.9999/update-to-test.1 - a hand-built fixture (Crossref's real {@code update-to[]}
     * field wasn't present on any live-captured fixture available when this was added) proving
     * {@code correction}/{@code retraction} dates map from {@code update-to[].updated} at the
     * golden-output level, and that an unrecognized {@code type} (here {@code "erratum"}) is
     * ignored rather than guessed at (see {@code CrossrefToSkgIfMapper#dates}).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_journalArticleWithUpdateTo() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.9999/update-to-test.1", "crossref-journal-article-with-update-to.json",
                "expected/crossref-journal-article-with-update-to-out.json");
    }

    /**
     * DOI 10.1155/2016/1353212 - a real Hindawi journal article with a funder that has neither
     * an award number nor a Funder Registry DOI, and a JATS-XML abstract - proving the otf
     * funder/abstract-stripping paths at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_journalArticleWithFunder() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.1155/2016/1353212", "crossref-journal-article-with-funder.json",
                "expected/crossref-journal-article-with-funder-out.json");
    }

    /**
     * DOI 10.1007/978-3-319-66787-4_9 - a real book chapter ({@code type: "book-chapter"}) whose
     * book is part of a series (LNCS). Crossref's XML transform is fetched for this type (see
     * {@code CrossrefTypeMapping#isXmlVenueEnrichable}) and takes precedence over the ambiguous
     * {@code container-title[]} array - proves the corrected book-title venue, the book's own
     * DOI as a real {@code local_identifier}, combined doi/issn/isbn identifiers, and the series
     * volume number at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_bookChapter() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.1007/978-3-319-66787-4_9", "crossref-book-chapter.json",
                "crossref-book-chapter.xml", "expected/crossref-book-chapter-out.json");
    }

    /**
     * DOI 10.1007/978-1-4842-7310-4_15 - a real book chapter whose book is standalone, not part
     * of any series (Crossref's XML uses {@code book_metadata} rather than {@code
     * book_series_metadata} here) - proves the no-series path at the golden-output level: book
     * title/DOI/ISBN still enrich the venue, but there's no series ISSN and no volume number.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_bookChapterStandalone() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.1007/978-1-4842-7310-4_15", "crossref-book-chapter-standalone.json",
                "crossref-book-chapter-standalone.xml", "expected/crossref-book-chapter-standalone-out.json");
    }

    /**
     * DOI 10.2991/assehr.k.211222.032 - a real proceedings-article (ICIRAD 2021, Atlantis Press)
     * whose {@code container-title[]} has the exact same series-vs-actual-title ambiguity as the
     * book-chapter case above, and whose {@code proceedings_series_metadata} has no
     * {@code doi_data} - proves the corrected proceedings-title venue, the otf-id
     * {@code local_identifier} fallback (no container DOI recorded here), combined issn/isbn
     * identifiers, and the series volume number at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_proceedingsArticleWithSeries() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.2991/assehr.k.211222.032", "crossref-proceedings-article-with-series.json",
                "crossref-proceedings-article-with-series.xml", "expected/crossref-proceedings-article-with-series-out.json");
    }

    /**
     * DOI 10.1109/freq.1998.717994 (the user's own example) - a real IEEE proceedings-article
     * whose proceedings isn't part of any series ({@code proceedings_metadata}, no {@code
     * proceedings_series_metadata}). Its REST JSON {@code container-title[0]} already happens to
     * match the XML's {@code proceedings_title}, but its ISBN is entirely absent from the REST
     * JSON - proves the XML-only ISBN enrichment path for proceedings at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_proceedingsArticleStandalone() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.1109/freq.1998.717994", "crossref-proceedings-article-standalone.json",
                "crossref-proceedings-article-standalone.xml", "expected/crossref-proceedings-article-standalone-out.json");
    }

    /**
     * DOI 10.19276/plinius.2019.01004 - a mEDRA-registered journal article whose single
     * contributor carries all four ONIX name fields together ({@code NamesBeforeKey}/{@code
     * KeyNames} and {@code PersonName}/{@code PersonNameInverted}) - proves the structured-pair
     * precedence at the golden-output level (see {@code MedraToSkgIfMapper#personRef}).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_medraMixedNameShapes() throws IOException {
        assertMatchesExpectedMedraJsonLd("10.19276/plinius.2019.01004", "medra-mixed-name-shapes.xml",
                "expected/medra-mixed-name-shapes-out.json");
    }

    /**
     * DOI 10.3254/978-1-61499-732-0-119 - a mEDRA-registered proceedings chapter (registered
     * under the {@code ...VersionRegistrationMessage} root variant, IOS Press book series
     * modeled as a journal) whose contributors carry only a bare {@code PersonName}, and whose
     * record has an abstract - proves the no-inverted-form fallback and abstract mapping at the
     * golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_medraVersionMessageBookSeries() throws IOException {
        assertMatchesExpectedMedraJsonLd("10.3254/978-1-61499-732-0-119", "medra-version-message-book-series.xml",
                "expected/medra-version-message-book-series-out.json");
    }

    /**
     * DOI 10.1393/ncc/i2025-25069-2 - a mEDRA-registered journal article with 23 authors, all
     * using the {@code NamesBeforeKey}/{@code KeyNames} name shape.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_medraManyAuthors() throws IOException {
        assertMatchesExpectedMedraJsonLd("10.1393/ncc/i2025-25069-2", "medra-many-authors.xml",
                "expected/medra-many-authors-out.json");
    }

    /**
     * DOI 10.1393/ncc/i2021-21084-7 - a mEDRA-registered journal article with zero
     * {@code Contributor} elements at all - proves {@code contributions} is omitted rather than
     * an empty list at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_medraNoContributors() throws IOException {
        assertMatchesExpectedMedraJsonLd("10.1393/ncc/i2021-21084-7", "medra-no-contributors.xml",
                "expected/medra-no-contributors-out.json");
    }

    /**
     * DOI 10.1478/AAPP.98S1A9 - a mEDRA-registered journal article whose journal carries 4
     * {@code Title} entries (2 languages x 2 title types) - proves the article-vs-journal title
     * disambiguation and the first-full-title-in-document-order venue-name heuristic at the
     * golden-output level (see {@code MedraOnixXmlParser#journalTitle}).
     */
    @Test
    void getProductById_matchesExpectedJsonLd_medraMultilangTitles() throws IOException {
        assertMatchesExpectedMedraJsonLd("10.1478/AAPP.98S1A9", "medra-multilang-titles.xml",
                "expected/medra-multilang-titles-out.json");
    }

    /**
     * DOI 10.12919/sapere.2018.04.3 - a mEDRA-registered journal article whose single contributor
     * carries only {@code PersonNameInverted}, with no {@code PersonName} sibling at all - proves
     * the invert-and-recompose fallback at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_medraPersonNameInvertedOnly() throws IOException {
        assertMatchesExpectedMedraJsonLd("10.12919/sapere.2018.04.3", "medra-personname-inverted-only.xml",
                "expected/medra-personname-inverted-only-out.json");
    }

    /**
     * DOI 10.1400/255846 - a mEDRA-registered journal article whose {@code SerialVersion} carries
     * two {@code ProductIdentifier} siblings (a proprietary id, {@code ProductIDType 01}, and the
     * ISSN, {@code ProductIDType 07}) rather than one-per-{@code SerialVersion} as in the other
     * fixtures - proves the ISSN-only filter still picks the right one when both share a parent.
     * Its {@code ContentItem} also has no {@code PublicationDate} at all (only a
     * {@code JournalIssueDate}, deliberately unmapped), and its sole contributor's
     * {@code PersonNameInverted} ("Camara Bastos, Maria Helena") has a two-word family name -
     * proves the split-on-first-comma-only behaviour at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_medraMultipleProductIdentifiers() throws IOException {
        assertMatchesExpectedMedraJsonLd("10.1400/255846", "medra-multiple-product-identifiers.xml",
                "expected/medra-multiple-product-identifiers-out.json");
    }

    private void assertMatchesExpectedMedraJsonLd(String doi, String medraXmlFixture, String expectedJsonLdResource)
            throws IOException {
        Response xmlResponse = okXmlResponse(medraXmlFixture);
        when(medraClient.getMetadata(eq(doi))).thenReturn(xmlResponse);

        String actualBody = given().when().get(BASE + "/medra/products/" + doi).then().statusCode(200).extract().asString();
        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), expectedJsonLdResource);
    }

    private void assertMatchesExpectedDataCiteJsonLd(String doi, String dataCiteFixture, String expectedJsonLdResource)
            throws IOException {
        when(dataCiteClient.getDoi(eq(doi))).thenReturn(loadDataCiteFixture(dataCiteFixture));

        String actualBody = given().when().get(BASE + "/datacite/products/" + doi).then().statusCode(200).extract().asString();
        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), expectedJsonLdResource);
    }

    private void assertMatchesExpectedCrossrefJsonLd(String doi, String crossrefFixture, String expectedJsonLdResource)
            throws IOException {
        when(crossrefClient.getWork(eq(doi))).thenReturn(loadCrossrefFixture(crossrefFixture));

        String actualBody = given().when().get(BASE + "/crossref/products/" + doi).then().statusCode(200).extract().asString();
        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), expectedJsonLdResource);
    }

    /**
     * Overload for chapter-in-a-book or paper-in-proceedings DOIs, additionally mocking the XML
     * transform fetch (see {@code CrossrefTypeMapping#isXmlVenueEnrichable}) that the plain
     * overload above never triggers.
     */
    private void assertMatchesExpectedCrossrefJsonLd(String doi, String crossrefFixture, String venueXmlFixture,
            String expectedJsonLdResource) throws IOException {
        when(crossrefClient.getWork(eq(doi))).thenReturn(loadCrossrefFixture(crossrefFixture));
        // Built as a separate statement, not inline as thenReturn(...)'s argument - okXmlResponse
        // itself opens Mockito when(...)/thenReturn(...) stubs, and evaluating it inside another
        // still-open when(...).thenReturn(...) call corrupts Mockito's single ongoing-stubbing
        // state (UnfinishedStubbingException).
        Response xmlResponse = okXmlResponse(venueXmlFixture);
        when(crossrefXmlTransformClient.getXmlTransform(eq(doi))).thenReturn(xmlResponse);

        String actualBody = given().when().get(BASE + "/crossref/products/" + doi).then().statusCode(200).extract().asString();
        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), expectedJsonLdResource);
    }

    private void compareOrWriteGolden(JsonNode actual, String expectedResource) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        if (REGENERATE_GOLDEN) {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File("src/test/resources/" + expectedResource), actual);
            return;
        }

        var expected = objectMapper.readTree(getClass().getClassLoader().getResourceAsStream(expectedResource));

        org.junit.jupiter.api.Assertions.assertEquals(expected, actual,
                "Actual JSON-LD output no longer matches " + expectedResource
                        + ". If this change is intentional: mvn test -Dtest=ProductsGoldenTest"
                        + " -Dgolden.regenerate=true, then review the diff before committing.");
    }
}
