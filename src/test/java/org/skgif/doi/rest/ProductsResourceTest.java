package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProductsResourceTest {

    private static final String BASE = "/skg-if/api";

    /**
     * Regenerate the golden JSON-LD files in src/test/resources/expected/ instead of asserting
     * against them - e.g. after an intentional DataCiteToSkgIfMapper change:
     * {@code mvn test -Dtest=ProductsResourceTest -Dgolden.regenerate=true}
     * Then `git diff` the expected/ files to review exactly what changed before committing.
     */
    private static final boolean REGENERATE_GOLDEN = Boolean.getBoolean("golden.regenerate");

    @InjectMock
    @RestClient
    DataCiteClient dataCiteClient;

    private DataCiteDoiResponse loadFixture() throws IOException {
        return loadFixture("datacite-esrf-dc-2493599001.json");
    }

    private DataCiteDoiResponse loadFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, DataCiteDoiResponse.class);
        }
    }

    @Test
    void getProductById_returnsSkgIfEnvelope() throws IOException {
        when(dataCiteClient.getDoi(eq("10.15151/esrf-dc-2493599001"))).thenReturn(loadFixture());

        given()
                .when().get(BASE + "/datacite/products/10.15151/esrf-dc-2493599001")
                .then()
                .statusCode(200)
                .body("@context", org.hamcrest.Matchers.hasSize(3))
                .body("'@graph'[0].local_identifier", equalTo("https://doi.org/10.15151/esrf-dc-2493599001"))
                .body("'@graph'[0].product_type", equalTo("research data"))
                .body("'@graph'[0].identifiers[0].scheme", equalTo("doi"));
    }

    /**
     * Simulates the exact string @PathParam receives once Vert.x's own path normalization has
     * collapsed a raw, unescaped "https://doi.org/..." local_identifier's "//" down to "/".
     * Hand-constructed with a single slash (rather than relying on RestAssured/the test HTTP
     * client to reproduce the real collapsing of a literal "//" mid-request) precisely because
     * a single slash isn't touched by any further normalization - this deterministically
     * exercises the full ProductsResource -> LocalIdentifiers.toDoi() -> DataCiteClient pipeline
     * with the same string shape LocalIdentifiersTest pins at the unit level.
     */
    @Test
    void getProductById_vertxCollapsedFullLocalIdentifier_returns200() throws IOException {
        when(dataCiteClient.getDoi(eq("10.15151/esrf-dc-2493599001"))).thenReturn(loadFixture());

        given()
                .when().get(BASE + "/datacite/products/https:/doi.org/10.15151/esrf-dc-2493599001")
                .then()
                .statusCode(200)
                .body("'@graph'[0].local_identifier", equalTo("https://doi.org/10.15151/esrf-dc-2493599001"));
    }

    @Test
    void getProductById_notFound_returns404WithRfc7807Error() {
        when(dataCiteClient.getDoi(any())).thenThrow(new NotFoundException());

        given()
                .when().get(BASE + "/datacite/products/10.9999/does-not-exist")
                .then()
                .statusCode(404)
                .body("status", equalTo("404"))
                .body("title", equalTo("NOT_FOUND"));
    }

    /**
     * Award-type DOIs are grants, not products - {@code /datacite/products/{id}} must 404
     * rather than exposing them, and the error should point the caller at
     * {@code /datacite/grants} instead.
     */
    @Test
    void getProductById_awardDoi_returns404PointingToGrants() throws IOException {
        when(dataCiteClient.getDoi(eq("10.3565/83eg-9981")))
                .thenReturn(loadFixture("datacite-award-ardc-83eg-9981.json"));

        given()
                .when().get(BASE + "/datacite/products/10.3565/83eg-9981")
                .then()
                .statusCode(404)
                .body("status", equalTo("404"))
                .body("detail", containsString("/datacite/grants/10.3565/83eg-9981"));
    }

    @Test
    void getProducts_excludesAwardsFromDataCiteQuery() {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse();
        listResponse.data = java.util.List.of();
        DataCiteDoiListResponse.Meta meta = new DataCiteDoiListResponse.Meta();
        meta.total = 0;
        meta.totalPages = 0;
        meta.page = 1;
        listResponse.meta = meta;
        when(dataCiteClient.listDois(any(), any(), anyInt(), anyInt())).thenReturn(listResponse);

        given().when().get(BASE + "/datacite/products").then().statusCode(200);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataCiteClient).listDois(any(), queryCaptor.capture(), anyInt(), anyInt());
        org.junit.jupiter.api.Assertions.assertTrue(
                queryCaptor.getValue().contains("NOT types.resourceTypeGeneral:Award"));
    }

    @Test
    void getProducts_invalidFilter_returns422() {
        given()
                .when().get(BASE + "/datacite/products?filter=bogus_filter:xyz")
                .then()
                .statusCode(422)
                .body("status", equalTo("422"))
                .body("title", equalTo("INVALID_FILTER"));
    }

    /**
     * Full JSON-LD output regression tests: the actual response body must exactly match
     * (structurally - key order doesn't matter) the corresponding checked-in document under
     * {@code src/test/resources/expected/}. These committed documents double as a live,
     * readable reference for what this API actually produces for a real ESRF dataset.
     */
    @Test
    void getProductById_matchesExpectedJsonLd_esrfDc2493599001() throws IOException {
        assertMatchesExpectedJsonLd("10.15151/esrf-dc-2493599001", "datacite-esrf-dc-2493599001.json",
                "expected/datacite-product-esrf-dc-2493599001.json");
    }

    @Test
    void getProductById_matchesExpectedJsonLd_esrfEs2210534378_withAffiliationsAndFunding() throws IOException {
        assertMatchesExpectedJsonLd("10.15151/esrf-es-2210534378", "datacite-esrf-es-2210534378.json",
                "expected/datacite-product-esrf-es-2210534378.json");
    }

    /**
     * Full JSON-LD regression test for the search/list endpoint with multiple, heterogeneous
     * @graph items and full pagination metadata (both prev_page and next_page present, unlike
     * getProducts_returnsSearchEnvelope below which only covers a single-item, single-page
     * response). Reuses the two DOI fixtures already exercised by the single-entity golden
     * tests above, so the two mapped products are known-good and no new DataCite network
     * capture is needed. Page 2 of 3 is the deliberate choice: it's the one scenario where both
     * prev_page and next_page are emitted simultaneously.
     */
    @Test
    void getProducts_matchesExpectedJsonLd_multipleItemsPage2Of3() throws IOException {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse();
        listResponse.data = java.util.List.of(
                loadFixture("datacite-esrf-dc-2493599001.json").data,
                loadFixture("datacite-esrf-es-2210534378.json").data);
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

        compareOrWriteGolden(new ObjectMapper().readTree(actualBody), "expected/datacite-products-search-multiple.json");
    }

    private void assertMatchesExpectedJsonLd(String doi, String dataCiteFixture, String expectedJsonLdResource)
            throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(dataCiteFixture)) {
            when(dataCiteClient.getDoi(eq(doi))).thenReturn(objectMapper.readValue(in, DataCiteDoiResponse.class));
        }

        String actualBody = given().when().get(BASE + "/datacite/products/" + doi).then().statusCode(200).extract().asString();
        compareOrWriteGolden(objectMapper.readTree(actualBody), expectedJsonLdResource);
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
                        + ". If this change is intentional: mvn test -Dtest=ProductsResourceTest"
                        + " -Dgolden.regenerate=true, then review the diff before committing.");
    }

    @Test
    void getProducts_returnsSearchEnvelope() throws IOException {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse();
        listResponse.data = java.util.List.of(loadFixture().data);
        DataCiteDoiListResponse.Meta meta = new DataCiteDoiListResponse.Meta();
        meta.total = 1;
        meta.totalPages = 1;
        meta.page = 1;
        listResponse.meta = meta;

        when(dataCiteClient.listDois(any(), any(), anyInt(), anyInt())).thenReturn(listResponse);

        given()
                .when().get(BASE + "/datacite/products?page_size=5")
                .then()
                .statusCode(200)
                .body("meta.entity_type", equalTo("search_result_page"))
                .body("meta.part_of.total_items", equalTo(1))
                .body("'@graph'[0].local_identifier", equalTo("https://doi.org/10.15151/esrf-dc-2493599001"))
                // api_items[i].local_identifier must equal the entity's own local_identifier
                // (@graph[i].local_identifier) per the spec's worked examples - NOT this API's
                // self URL, which belongs solely in urls[].href. Pinned directly here since a
                // full golden-file diff can be easy to skim past.
                .body("meta.api_items[0].local_identifier", equalTo("https://doi.org/10.15151/esrf-dc-2493599001"))
                .body("meta.api_items[0].urls[0].href",
                        equalTo("http://localhost:8081/skg-if/api/datacite/products/10.15151/esrf-dc-2493599001"));
    }
}
