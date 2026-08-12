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
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skgif.doi.datacite.DataCiteClient;
import org.skgif.doi.datacite.dto.DataCiteDoiListResponse;
import org.skgif.doi.datacite.dto.DataCiteDoiResponse;

@QuarkusTest
class GrantsResourceTest {

    private static final String BASE = "/skg-if/api";

    /**
     * Regenerate the golden JSON-LD files in src/test/resources/expected/ instead of asserting
     * against them - see {@code ProductsResourceTest}'s equivalent flag for the full rationale:
     * {@code mvn test -Dtest=GrantsResourceTest -Dgolden.regenerate=true}
     */
    private static final boolean REGENERATE_GOLDEN = Boolean.getBoolean("golden.regenerate");

    @InjectMock
    @RestClient
    DataCiteClient dataCiteClient;

    private DataCiteDoiResponse loadFixture(String resourceName) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return objectMapper.readValue(in, DataCiteDoiResponse.class);
        }
    }

    @Test
    void getGrantById_returnsSkgIfEnvelope() throws IOException {
        when(dataCiteClient.getDoi(eq("10.3565/83eg-9981")))
                .thenReturn(loadFixture("datacite-award-ardc-83eg-9981.json"));

        given()
                .when().get(BASE + "/datacite/grants/10.3565/83eg-9981")
                .then()
                .statusCode(200)
                .body("@context", org.hamcrest.Matchers.hasSize(3))
                .body("'@graph'[0].local_identifier", equalTo("https://doi.org/10.3565/83eg-9981"))
                .body("'@graph'[0].entity_type", equalTo("grant"))
                .body("'@graph'[0].identifiers[0].scheme", equalTo("doi"))
                .body("'@graph'[0].funding_agency.name", equalTo("Australian Research Data Commons"))
                .body("'@graph'[0].beneficiaries[0].name", equalTo("Atlas of Living Australia"))
                // Guards against the generator's polymorphic @JsonTypeInfo on GrantContributionBy
                // overriding this with its @JsonTypeName ("GrantContribution_by") instead - see
                // SkgIfObjectMapperCustomizer's javadoc.
                .body("'@graph'[0].contributions[0].by.entity_type", equalTo("organisation"));
    }

    /**
     * Full JSON-LD output regression test, mirroring {@code ProductsResourceTest}'s: the actual
     * response body must exactly match (structurally) the checked-in document under
     * {@code expected/datacite-grant-award-ardc-83eg-9981.json} - a real Award DOI from the Australian
     * Research Data Commons, chosen as a clean, minimal real-world example (single ROR-bearing
     * creator as funding_agency, single ROR-bearing organisational contributor as both a
     * contribution and a beneficiary, no funding_references/dates/rights_list noise).
     */
    @Test
    void getGrantById_matchesExpectedJsonLd_ardc83eg9981() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        when(dataCiteClient.getDoi(eq("10.3565/83eg-9981")))
                .thenReturn(loadFixture("datacite-award-ardc-83eg-9981.json"));

        String actualBody = given()
                .when().get(BASE + "/datacite/grants/10.3565/83eg-9981")
                .then()
                .statusCode(200)
                .extract().asString();

        compareOrWriteGolden(objectMapper.readTree(actualBody), "expected/datacite-grant-award-ardc-83eg-9981.json");
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
                        + ". If this change is intentional: mvn test -Dtest=GrantsResourceTest"
                        + " -Dgolden.regenerate=true, then review the diff before committing.");
    }

    @Test
    void getGrantById_notFound_returns404WithRfc7807Error() {
        when(dataCiteClient.getDoi(any())).thenThrow(new NotFoundException());

        given()
                .when().get(BASE + "/datacite/grants/10.9999/does-not-exist")
                .then()
                .statusCode(404)
                .body("status", equalTo("404"))
                .body("title", equalTo("NOT_FOUND"));
    }

    /**
     * A non-Award DOI (a product) requested via {@code /datacite/grants} must 404, pointing the
     * caller at {@code /datacite/products} instead - the inverse of {@code
     * ProductsResourceTest#getProductById_awardDoi_returns404PointingToGrants}.
     */
    @Test
    void getGrantById_productDoi_returns404PointingToProducts() throws IOException {
        when(dataCiteClient.getDoi(eq("10.15151/esrf-dc-2493599001")))
                .thenReturn(loadFixture("datacite-esrf-dc-2493599001.json"));

        given()
                .when().get(BASE + "/datacite/grants/10.15151/esrf-dc-2493599001")
                .then()
                .statusCode(404)
                .body("status", equalTo("404"))
                .body("detail", containsString("/datacite/products/10.15151/esrf-dc-2493599001"));
    }

    @Test
    void getGrants_invalidFilter_returns422() {
        given()
                .when().get(BASE + "/datacite/grants?filter=bogus_filter:xyz")
                .then()
                .statusCode(422)
                .body("status", equalTo("422"))
                .body("title", equalTo("INVALID_FILTER"));
    }

    @Test
    void getGrants_onlyIncludesAwardsInDataCiteQuery() {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse();
        listResponse.data = java.util.List.of();
        DataCiteDoiListResponse.Meta meta = new DataCiteDoiListResponse.Meta();
        meta.total = 0;
        meta.totalPages = 0;
        meta.page = 1;
        listResponse.meta = meta;
        when(dataCiteClient.listDois(any(), any(), anyInt(), anyInt())).thenReturn(listResponse);

        given().when().get(BASE + "/datacite/grants").then().statusCode(200);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataCiteClient).listDois(any(), queryCaptor.capture(), anyInt(), anyInt());
        org.junit.jupiter.api.Assertions.assertTrue(
                queryCaptor.getValue().contains("types.resourceTypeGeneral:Award"));
    }

    @Test
    void getGrants_returnsSearchEnvelope() throws IOException {
        DataCiteDoiListResponse listResponse = new DataCiteDoiListResponse();
        listResponse.data = java.util.List.of(loadFixture("datacite-award-ardc-83eg-9981.json").data);
        DataCiteDoiListResponse.Meta meta = new DataCiteDoiListResponse.Meta();
        meta.total = 1;
        meta.totalPages = 1;
        meta.page = 1;
        listResponse.meta = meta;

        when(dataCiteClient.listDois(any(), any(), anyInt(), anyInt())).thenReturn(listResponse);

        given()
                .when().get(BASE + "/datacite/grants?page_size=5")
                .then()
                .statusCode(200)
                .body("meta.entity_type", equalTo("search_result_page"))
                .body("meta.part_of.total_items", equalTo(1))
                .body("'@graph'[0].local_identifier", equalTo("https://doi.org/10.3565/83eg-9981"))
                .body("meta.api_items[0].local_identifier", equalTo("https://doi.org/10.3565/83eg-9981"))
                .body("meta.api_items[0].urls[0].href",
                        equalTo("http://localhost:8081/skg-if/api/datacite/grants/10.3565/83eg-9981"));
    }
}
