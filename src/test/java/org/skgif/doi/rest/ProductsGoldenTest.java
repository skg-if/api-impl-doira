package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.skgif.doi.crossref.CrossrefClient;
import org.skgif.doi.crossref.dto.CrossrefWorkResponse;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;

/**
 * Full JSON-LD output regression tests for {@code /datacite/products} and {@code
 * /crossref/products}: the actual response body must exactly match (structurally - key order
 * doesn't matter) the corresponding checked-in document under {@code
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
     * DOI 10.1155/2016/1353212 - a real Hindawi journal article with a funder that has neither
     * an award number nor a Funder Registry DOI, and a JATS-XML abstract - proving the otf
     * funder/abstract-stripping paths at the golden-output level.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_journalArticleWithFunder() throws IOException {
        assertMatchesExpectedCrossrefJsonLd("10.1155/2016/1353212", "crossref-journal-article-with-funder.json",
                "expected/crossref-journal-article-with-funder-out.json");
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
