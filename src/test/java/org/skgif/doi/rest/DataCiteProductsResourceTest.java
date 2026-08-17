package org.skgif.doi.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

/**
 * Golden JSON-LD output regression tests live in {@link ProductsGoldenTest}.
 */
@QuarkusTest
class DataCiteProductsResourceTest {

    private static final String BASE = "/skg-if/api";

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
     * exercises the full DataCiteProductsResource -> LocalIdentifiers.toDoi() -> DataCiteClient pipeline
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
        when(dataCiteClient.getDoi(eq("10.71707/r3sy-7371")))
                .thenReturn(loadFixture("datacite-award-r3sy-7371.json"));

        given()
                .when().get(BASE + "/datacite/products/10.71707/r3sy-7371")
                .then()
                .statusCode(404)
                .body("status", equalTo("404"))
                .body("detail", containsString("/datacite/grants/10.71707/r3sy-7371"));
    }

    @Test
    void getProducts_excludesAwardsFromDataCiteQuery() {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse(
                java.util.List.of(), new DataCiteDoiListResponse.Meta(0, 0, 1), null);
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

    @Test
    void getProducts_returnsSearchEnvelope() throws IOException {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse(
                java.util.List.of(loadFixture().data()), new DataCiteDoiListResponse.Meta(1, 1, 1), null);

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
