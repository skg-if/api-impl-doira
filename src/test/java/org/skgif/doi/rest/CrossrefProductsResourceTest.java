package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.CrossrefXmlTransformClient;
import org.skgif.doi.crossref.dto.CrossrefWorkListResponse;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;

/**
 * The Crossref-provider sibling of {@code DataCiteProductsResourceTest}, at the separate {@code
 * /crossref/products} path (see {@code CrossrefProductsResource}'s javadoc for why provider
 * selection is URL-driven rather than auto-detected). Golden JSON-LD output regression tests
 * live in {@link ProductsGoldenTest}.
 */
@QuarkusTest
class CrossrefProductsResourceTest {

    /** Base path this API is served under. */
    private static final String BASE = "/skg-if/api";

    /** HTTP 200 OK status code. */
    private static final int HTTP_OK = 200;
    /** HTTP 404 Not Found status code. */
    private static final int HTTP_NOT_FOUND = 404;
    /** HTTP 422 Unprocessable Entity status code. */
    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;
    /** Expected {@code @context} array size for a single-entity response. */
    private static final int EXPECTED_CONTEXT_SIZE = 3;

    /** Mocked Crossref REST client, stubbed per test case. */
    @InjectMock
    @RestClient
    CrossrefClient crossrefClient;

    /** Mocked Crossref XML-transform REST client, stubbed per test case. */
    @InjectMock
    @RestClient
    CrossrefXmlTransformClient crossrefXmlTransformClient;

    private CrossrefWorkResponse loadFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, CrossrefWorkResponse.class);
        }
    }

    private CrossrefWorkListResponse loadWorkListFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, CrossrefWorkListResponse.class);
        }
    }

    private Response okXmlResponse(String xmlResourceName) throws IOException {
        return XmlFixtureResponses.okXmlResponse(getClass(), xmlResourceName);
    }

    @Test
    void getProductById_returnsSkgIfEnvelope() throws IOException {
        when(crossrefClient.getWork("10.1038/nature12373"))
                .thenReturn(loadFixture("crossref-journal-article.json"));

        given()
                .when().get(BASE + "/crossref/products/10.1038/nature12373")
                .then()
                .statusCode(HTTP_OK)
                .body("@context", Matchers.hasSize(EXPECTED_CONTEXT_SIZE))
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/nature12373"))
                .body("'@graph'[0].product_type", Matchers.equalTo("literature"))
                .body("'@graph'[0].identifiers[0].scheme", Matchers.equalTo("doi"));
    }

    /**
     * Nature's ISSN (0028-0836) resolves live to a real Crossref {@code type: "journal"} DOI
     * ({@code 10.1038/41586.1476-4687} - see {@code crossref-journal-doi-lookup-nature.json},
     * captured from the real API) - see {@code CrossrefJournalDoiResolver}. Once found, the
     * venue's {@code local_identifier} becomes that real DOI URL instead of an otf id, with a
     * {@code doi} identifier alongside the existing {@code issn} ones.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_returnsSkgIfEnvelope_venueUsesRealJournalDoiWhenResolved() throws IOException {
        when(crossrefClient.getWork("10.1038/nature12373"))
                .thenReturn(loadFixture("crossref-journal-article.json"));
        when(crossrefClient.listWorks(eq("type:journal,issn:0028-0836"), any(), any(), eq(1), any(), any()))
                .thenReturn(loadWorkListFixture("crossref-journal-doi-lookup-nature.json"));

        given()
                .when().get(BASE + "/crossref/products/10.1038/nature12373")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].manifestations[0].biblio.in.local_identifier",
                        Matchers.equalTo("https://doi.org/10.1038/41586.1476-4687"))
                .body("'@graph'[0].manifestations[0].biblio.in.identifiers.scheme",
                        Matchers.hasItems("doi", "issn"));
    }

    /**
     * When the journal-DOI lookup itself fails (network error, timeout, etc.), the product
     * response must still succeed, falling back to the existing otf-id venue rather than the
     * whole request failing - same degrade-gracefully contract as the XML transform fetch below.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_venueFallsBackToOtfIdWhenJournalDoiLookupFails() throws IOException {
        when(crossrefClient.getWork("10.1038/nature12373"))
                .thenReturn(loadFixture("crossref-journal-article.json"));
        when(crossrefClient.listWorks(eq("type:journal,issn:0028-0836"), any(), any(), eq(1), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        given()
                .when().get(BASE + "/crossref/products/10.1038/nature12373")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].manifestations[0].biblio.in.local_identifier", Matchers.startsWith("otf___"));
    }

    /**
     * DOI 10.1038/s41467-022-33468-6 - a real Nature Communications article whose authors carry
     * ORCIDs and which has no "page" field, unlike the {@code nature12373} fixture above.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_returnsSkgIfEnvelope_orcidArticle() throws IOException {
        when(crossrefClient.getWork("10.1038/s41467-022-33468-6"))
                .thenReturn(loadFixture("crossref-journal-article-with-orcid.json"));

        given()
                .when().get(BASE + "/crossref/products/10.1038/s41467-022-33468-6")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/s41467-022-33468-6"))
                .body("'@graph'[0].product_type", Matchers.equalTo("literature"))
                .body("'@graph'[0].identifiers[0].scheme", Matchers.equalTo("doi"))
                .body("'@graph'[0].contributions[0].by.identifiers[0].scheme", Matchers.equalTo("orcid"))
                .body("'@graph'[0].manifestations[0].access_rights.status", Matchers.equalTo("open"));
    }

    /**
     * DOI 10.17537/icmbb18.42 - a real conference-proceedings record (type:
     * "proceedings-article"), unlike the journal-article fixtures above.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_returnsSkgIfEnvelope_proceedingsArticle() throws IOException {
        when(crossrefClient.getWork("10.17537/icmbb18.42"))
                .thenReturn(loadFixture("crossref-proceedings-article.json"));

        given()
                .when().get(BASE + "/crossref/products/10.17537/icmbb18.42")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.17537/icmbb18.42"))
                .body("'@graph'[0].product_type", Matchers.equalTo("literature"))
                .body("'@graph'[0].identifiers[0].scheme", Matchers.equalTo("doi"));
    }

    /**
     * DOI 10.1103/physrevb.110.174515 - a real Physical Review B article whose author
     * affiliations carry a ROR directly, unlike the other journal-article fixtures above.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_returnsSkgIfEnvelope_rorAffiliationArticle() throws IOException {
        when(crossrefClient.getWork("10.1103/physrevb.110.174515"))
                .thenReturn(loadFixture("crossref-journal-article-with-ror-affiliation.json"));

        given()
                .when().get(BASE + "/crossref/products/10.1103/physrevb.110.174515")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1103/physrevb.110.174515"))
                .body("'@graph'[0].product_type", Matchers.equalTo("literature"))
                .body("'@graph'[0].contributions[0].declared_affiliations[0].identifiers[0].scheme",
                        Matchers.equalTo("ror"))
                .body("'@graph'[0].contributions[0].declared_affiliations[0].identifiers[0].value",
                        Matchers.equalTo("00tmb7y09"));
    }

    /**
     * DOI 10.1007/978-3-319-66787-4_9 - a real book chapter ({@code type: "book-chapter"})
     * whose {@code container-title[]} has two entries (series, then book title), unlike the
     * single-entry journal-article fixtures above. Crossref's XML transform endpoint is fetched
     * for this type (see {@code CrossrefTypeMapping#isXmlVenueEnrichable}) and takes precedence:
     * the venue is named after the actual book, not the series, with a real DOI-based
     * local_identifier and doi/issn/isbn identifiers together.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_returnsSkgIfEnvelope_bookChapter() throws IOException {
        when(crossrefClient.getWork("10.1007/978-3-319-66787-4_9"))
                .thenReturn(loadFixture("crossref-book-chapter.json"));
        // Built as a separate statement - see okXmlResponse's javadoc for why this can't be
        // inlined as another when(...).thenReturn(...)'s argument.
        Response xmlResponse = okXmlResponse("crossref-book-chapter.xml");
        when(crossrefXmlTransformClient.getXmlTransform("10.1007/978-3-319-66787-4_9")).thenReturn(xmlResponse);

        given()
                .when().get(BASE + "/crossref/products/10.1007/978-3-319-66787-4_9")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1007/978-3-319-66787-4_9"))
                .body("'@graph'[0].product_type", Matchers.equalTo("literature"))
                .body("'@graph'[0].manifestations[0].biblio.in.name",
                        Matchers.equalTo("Cryptographic Hardware and Embedded Systems – CHES 2017"))
                .body("'@graph'[0].manifestations[0].biblio.in.local_identifier",
                        Matchers.equalTo("https://doi.org/10.1007/978-3-319-66787-4"))
                .body("'@graph'[0].manifestations[0].biblio.in.identifiers.scheme",
                        Matchers.hasItems("doi", "issn", "isbn"))
                .body("'@graph'[0].manifestations[0].biblio.volume", Matchers.equalTo("10529"));
    }

    /**
     * When the XML transform fetch fails (any non-200 response, timeout, or thrown exception),
     * the product response must still succeed, falling back to the existing
     * {@code container-title[0]} venue rather than the XML-enriched one - see
     * {@code CrossrefProductsResource#fetchVenueMetadata}.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_bookChapter_fallsBackToContainerTitleWhenXmlFetchFails() throws IOException {
        when(crossrefClient.getWork("10.1007/978-3-319-66787-4_9"))
                .thenReturn(loadFixture("crossref-book-chapter.json"));
        when(crossrefXmlTransformClient.getXmlTransform("10.1007/978-3-319-66787-4_9"))
                .thenThrow(new NotFoundException());

        given()
                .when().get(BASE + "/crossref/products/10.1007/978-3-319-66787-4_9")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].manifestations[0].biblio.in.name",
                        Matchers.equalTo("Lecture Notes in Computer Science"));
    }

    /**
     * DOI 10.2991/assehr.k.211222.032 - a real proceedings-article whose {@code container-title[]}
     * has the same series-vs-actual-title ambiguity as the book chapter case above. Crossref's
     * XML transform is fetched for this type too (see {@code
     * CrossrefTypeMapping#isXmlVenueEnrichable}) and corrects the venue name; this record's
     * {@code proceedings_series_metadata} has no {@code doi_data}, so the venue's
     * local_identifier falls back to an otf id rather than a real DOI URL.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_returnsSkgIfEnvelope_proceedingsArticleWithSeries() throws IOException {
        when(crossrefClient.getWork("10.2991/assehr.k.211222.032"))
                .thenReturn(loadFixture("crossref-proceedings-article-with-series.json"));
        Response xmlResponse = okXmlResponse("crossref-proceedings-article-with-series.xml");
        when(crossrefXmlTransformClient.getXmlTransform("10.2991/assehr.k.211222.032")).thenReturn(xmlResponse);

        given()
                .when().get(BASE + "/crossref/products/10.2991/assehr.k.211222.032")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.2991/assehr.k.211222.032"))
                .body("'@graph'[0].product_type", Matchers.equalTo("literature"))
                .body("'@graph'[0].manifestations[0].biblio.in.name", Matchers.equalTo(
                        "Proceedings of the 4th International Conference on Innovative Research Across Disciplines " +
                                "(ICIRAD 2021)"))
                .body("'@graph'[0].manifestations[0].biblio.in.local_identifier", Matchers.startsWith("otf___"))
                .body("'@graph'[0].manifestations[0].biblio.in.identifiers.scheme",
                        Matchers.hasItems("issn", "isbn"))
                .body("'@graph'[0].manifestations[0].biblio.volume", Matchers.equalTo("613"));
    }

    @Test
    void getProductById_notFound_returns404WithRfc7807Error() {
        when(crossrefClient.getWork(any())).thenThrow(new NotFoundException());

        given()
                .when().get(BASE + "/crossref/products/10.9999/does-not-exist")
                .then()
                .statusCode(HTTP_NOT_FOUND)
                .body("status", Matchers.equalTo("404"))
                .body("title", Matchers.equalTo("NOT_FOUND"));
    }

    /**
     * A Crossref {@code type: "grant"} DOI requested via {@code /crossref/products} must 404,
     * pointing the caller at {@code /crossref/grants} instead - mirrors {@code
     * DataCiteProductsResourceTest#getProductById_awardDoi_returns404PointingToGrants}.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProductById_grantDoi_returns404PointingToGrants() throws IOException {
        when(crossrefClient.getWork("10.35802/218300")).thenReturn(loadFixture("crossref-grant.json"));

        given()
                .when().get(BASE + "/crossref/products/10.35802/218300")
                .then()
                .statusCode(HTTP_NOT_FOUND)
                .body("status", Matchers.equalTo("404"))
                .body("detail", Matchers.containsString("/crossref/grants/10.35802/218300"));
    }

    @Test
    void getProducts_invalidFilter_returns422() {
        given()
                .when().get(BASE + "/crossref/products?filter=bogus_filter:xyz")
                .then()
                .statusCode(HTTP_UNPROCESSABLE_ENTITY)
                .body("status", Matchers.equalTo("422"))
                .body("title", Matchers.equalTo("INVALID_FILTER"));
    }

    /**
     * Crossref's {@code filter=} has no negation operator (see {@code CrossrefFilters}), so
     * grant-type records are excluded from {@code /crossref/products} list results client-side
     * rather than via the query itself - this pins that exclusion.
     *
     * @throws IOException if a fixture resource cannot be read
     */
    @Test
    void getProducts_excludesGrantTypeRecordsFromResults() throws IOException {
        CrossrefWorkListResponse listResponse = new CrossrefWorkListResponse(null,
                new CrossrefWorkListResponse.Message(2, List.of(
                        loadFixture("crossref-journal-article.json").message(),
                        loadFixture("crossref-grant.json").message())));
        when(crossrefClient.listWorks(any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(listResponse);

        given()
                .when().get(BASE + "/crossref/products?page_size=5")
                .then()
                .statusCode(HTTP_OK)
                .body("'@graph'", Matchers.hasSize(1))
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/nature12373"));
    }

    @Test
    void getProducts_returnsSearchEnvelope() throws IOException {
        CrossrefWorkListResponse listResponse = new CrossrefWorkListResponse(null,
                new CrossrefWorkListResponse.Message(1,
                        List.of(loadFixture("crossref-journal-article.json").message())));
        when(crossrefClient.listWorks(any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(listResponse);

        given()
                .when().get(BASE + "/crossref/products?page_size=5")
                .then()
                .statusCode(HTTP_OK)
                .body("meta.entity_type", Matchers.equalTo("search_result_page"))
                .body("meta.part_of.total_items", Matchers.equalTo(1))
                .body("'@graph'[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/nature12373"))
                .body("meta.api_items[0].local_identifier", Matchers.equalTo("https://doi.org/10.1038/nature12373"))
                .body("meta.api_items[0].urls[0].href",
                        Matchers.equalTo("http://localhost:8081/skg-if/api/crossref/products/10.1038/nature12373"));
    }
}
